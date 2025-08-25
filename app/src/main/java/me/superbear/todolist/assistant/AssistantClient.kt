@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package me.superbear.todolist.assistant

import android.util.Log
import io.ktor.client.engine.cio.CIO
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.domain.entities.ChatMessage
import java.net.UnknownHostException

interface AssistantClient {
    suspend fun send(message: String, history: List<ChatMessage>): Result<String>
}

class MockAssistantClient : AssistantClient {
    private val responses = listOf(
        "{\"say\":\"I can help. What's the task?\",\"actions\":[]}",
        "{\"say\":\"Added to your list.\",\"actions\":[{\"type\":\"add_task\",\"title\":\"Buy milk\",\"notes\":\"2%\"}]}"
    )

    override suspend fun send(message: String, history: List<ChatMessage>): Result<String> {
        delay(1000)
        return Result.success(responses[Random.nextInt(responses.size)])
    }
}

// --- Logging helpers (only used for prettier logs) ---
private val prettyJson by lazy { Json { prettyPrint = true } }

private fun prettyWholeJson(text: String): String = runCatching {
    val element = Json.parseToJsonElement(text)
    prettyJson.encodeToString(element)
}.getOrElse { text }

// Try to pretty-print embedded JSON inside a string. If no valid JSON is found, return original text.
private fun prettyEmbeddedJson(text: String): String {
    // Heuristic: find the first top-level '{' or '[' and the last matching '}' or ']'
    fun extractCandidate(s: String): String? {
        val startObj = s.indexOf('{')
        val startArr = s.indexOf('[')
        val start = listOf(startObj, startArr).filter { it >= 0 }.minOrNull() ?: return null
        val endObj = s.lastIndexOf('}')
        val endArr = s.lastIndexOf(']')
        val end = maxOf(endObj, endArr)
        if (end > start) return s.substring(start, end + 1)
        return null
    }

    val candidate = extractCandidate(text) ?: return text
    return runCatching {
        val element = Json.parseToJsonElement(candidate)
        val pretty = prettyJson.encodeToString(element)
        // Replace only the candidate part with pretty version to preserve any prefixes like labels
        text.replaceRange(text.indexOf(candidate), text.indexOf(candidate) + candidate.length, pretty)
    }.getOrElse { text }
}

class RealAssistantClient : AssistantClient {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false  // 传输时使用紧凑格式
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
        install(Logging) {
            level = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
        }
    }

    private val apiKey: String = BuildConfig.OPENAI_API_KEY
    private val baseUrl: String = (try { BuildConfig::class.java.getField("OPENAI_BASE_URL").get(null) as? String } catch (_: Exception) { null })
        ?: "https://api.openai.com"

    var stateProvider: (() -> String)? = null

    // --- Debug logging helpers ---
    private fun logRequest(request: OpenAIRequest, messages: List<OpenAIChatMessage>) {
        Log.d("RealAssistantClient", "Request (pretty): ${prettyWholeJson(json.encodeToString(request))}")
        val prettyContents = messages.joinToString(separator = "\n") { m ->
            "- ${m.role}:\n" + prettyEmbeddedJson(m.content)
        }
        Log.d("RealAssistantClient", "Request message contents (pretty):\n$prettyContents")
    }

    private fun logResponse(response: String, assistantContent: String?) {
        Log.d("RealAssistantClient", "Response (pretty): ${prettyWholeJson(response)}")
        assistantContent?.let {
            Log.d("RealAssistantClient", "Response content (pretty):\n${prettyEmbeddedJson(it)}")
        }
    }

    private fun parseResponse(response: String): Result<String> {
        return try {
            val openAIResponse = json.decodeFromString<OpenAIResponse>(response)
            val assistantContent = openAIResponse.choices.firstOrNull()?.message?.content
            logResponse(response, assistantContent)
            Result.success(openAIResponse.choices.first().message.content)
        } catch (e: MissingFieldException) {
            handleParsingError(response, e)
        }
    }

    private fun handleParsingError(response: String, e: MissingFieldException): Result<String> {
        return try {
            val errorResponse = json.decodeFromString<OpenAIErrorResponse>(response)
            Result.failure(Exception("API Error: ${errorResponse.error.message}"))
        } catch (_: Exception) {
            Result.failure(Exception("Failed to parse API response: ${e.message}"))
        }
    }

    override suspend fun send(message: String, history: List<ChatMessage>): Result<String> {
        if (apiKey.isBlank() || apiKey.equals("null", ignoreCase = true)) {
            return Result.failure(Exception("OpenAI API key is missing. Please add it to your local.properties file."))
        }

        val systemInstruction = OpenAIChatMessage(
            role = "system",
            content = """You are a helpful assistant for a to-do list app. Reply with ONE compact JSON object ONLY (no code fences, no extra prose) using this schema:

{
  "say": "string",
  "actions": [
    { "type": "add_task", "title": "string", "notes": "string?", "dueAt": "ISO-8601?", "priority": "LOW|MEDIUM|HIGH|DEFAULT?", "parentId": 123? },
    { "type": "complete_task", "id": 123 },
    { "type": "delete_task", "id": 123 },
    { "type": "update_task", "id": 123, "title": "string?", "notes": "string?", "dueAt": "ISO-8601?", "priority": "LOW|MEDIUM|HIGH|DEFAULT?", "parentId": 456? }
  ]
}

Rules:
- You will also receive CURRENT_TODO_STATE as a system message. It is NESTED by hierarchy:
  {
    "now": "...",
    "unfinished": [ { "id": 1, "title": "...", "status": "OPEN", "priority": "...", "children": [ ... ], "totalChildren": 2, "doneChildren": 1, "progress": 0.5 }, ... ],
    "finished":   [ { "id": 2, "title": "...", "status": "DONE", "children": [ ... ] }, ... ],
    "finished_count": 42
  }
- Use ids from CURRENT_TODO_STATE when referencing existing items at ANY depth (top-level or nested children).
- complete_task: ONLY for items that are currently in the "unfinished" tree (any depth).
- delete_task: allowed for ANY item in either tree.
- add_task: for new items. Provide parentId to create a subtask under that parent; omit parentId to create a top-level task.
- update_task: use parentId to reparent under another existing task if needed.
- If the user asks to delete all/everything, return delete_task for all ids across BOTH trees.
- If ambiguous, ask a clarifying question in "say" and return "actions": [].
- Output EXACTLY one JSON object with keys "say" and "actions". No markdown/backticks/extra keys."""
        )

        val stateMessage = stateProvider?.invoke()?.let { snapshot ->
            OpenAIChatMessage(role = "system", content = "CURRENT_TODO_STATE: $snapshot")
        }

        // Always include system + CURRENT_TODO_STATE; trim only the chat history to fit the limit
        val historyMessages = history.map {
            OpenAIChatMessage(
                role = it.sender.name.lowercase(),
                content = it.text
            )
        }
        // Reserve 2 slots for system + state (state may be null; safe to over-reserve)
        val maxTotal = 10
        val reserved = 2
        val limitedHistory = historyMessages.takeLast(maxTotal - reserved)

        val messages = mutableListOf<OpenAIChatMessage>().apply {
            add(systemInstruction)
            stateMessage?.let { add(it) }
            addAll(limitedHistory)
        }

        val request = OpenAIRequest(
            model = "gpt-5-mini",
            messages = messages
        )
        
        return try {
            logRequest(request, messages)
            
            val response = client.post("${baseUrl.trimEnd('/')}/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<String>()
            
            parseResponse(response)
        } catch (e: HttpRequestTimeoutException) {
            Result.failure(Exception("Request timed out. Please check your internet connection and try again."))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Unable to resolve host. Check your DNS/proxy or network connectivity."))
        } catch (e: Exception) {
            Result.failure(Exception("An unexpected error occurred: ${e.message}"))
        }
    }
}

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIChatMessage>
)

@Serializable
data class OpenAIResponse(
    val choices: List<OpenAIChoice>
)

@Serializable
data class OpenAIChoice(
    val message: OpenAIChatMessage
)

@Serializable
data class OpenAIChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIErrorResponse(
    val error: OpenAIError
)

@Serializable
data class OpenAIError(
    val message: String
)
