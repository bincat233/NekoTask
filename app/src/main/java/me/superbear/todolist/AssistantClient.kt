@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package me.superbear.todolist

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
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

interface AssistantClient {
    suspend fun send(message: String, history: List<ChatMessage>): Result<String>
}

class MockAssistantClient : AssistantClient {
    private val responses = listOf(
        "{\"say\":\"I can help. What’s the task?\",\"actions\":[]}",
        "{\"say\":\"Added to your list.\",\"actions\":[{\"type\":\"add_task\",\"title\":\"Buy milk\",\"notes\":\"2%\"}]}"
    )

    override suspend fun send(message: String, history: List<ChatMessage>): Result<String> {
        delay(1000)
        return Result.success(responses[Random.nextInt(responses.size)])
    }
}

class RealAssistantClient : AssistantClient {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val apiKey: String = BuildConfig.OPENAI_API_KEY

    var stateProvider: (() -> String)? = null

    override suspend fun send(message: String, history: List<ChatMessage>): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("OpenAI API key is missing. Please add it to your local.properties file."))
        }

        val systemInstruction = OpenAIChatMessage(
            role = "system",
            content = """You are a helpful assistant for a to-do list app. You MUST reply with ONE compact JSON object ONLY (no code fences, no extra prose). Use this schema:

{
  "say": "string",
  "actions": [
    { "type": "add_task", "title": "string", "notes": "string?", "dueAt": "ISO-8601?", "priority": "LOW|MEDIUM|HIGH|DEFAULT?" },
    { "type": "complete_task", "id": 123 },
    { "type": "delete_task", "id": 123 },
    { "type": "update_task", "id": 123, "title": "string?", "notes": "string?", "dueAt": "ISO-8601?", "priority": "LOW|MEDIUM|HIGH|DEFAULT?" }
  ]
}

Rules:
- You will also receive CURRENT_TODO_STATE as a system message. It contains:
  { "now": "...", "unfinished": [ {"id":1, "title":"...", "priority":"..."}, ... ], "finished_count": N }.
- When the user wants to mark an existing item done → return { "type": "complete_task", "id": <the matching unfinished task id> }.
- When the user wants to delete/remove/stop an existing item → return { "type": "delete_task", "id": <the matching unfinished task id> }.
- ALWAYS reference existing items by their "id" from CURRENT_TODO_STATE.unfinished. Do NOT invent ids. Use case-insensitive fuzzy title matching.
- If no clear match is found or there are multiple matches, ask a clarifying question in "say" and return "actions": [].
- Only use add_task for genuinely new items the user wants to create.
- If no action is needed, return { "say": "...", "actions": [] }.

Output:
- Return EXACTLY one JSON object with the keys "say" and "actions". No markdown, no backticks, no extra keys.

Examples:

User: "I don’t want to chase the red dot anymore — delete it"
CURRENT_TODO_STATE.unfinished contains {"id":1, "title":"Chase the red dot"}
→ {"say":"Deleted 'Chase the red dot'.","actions":[{"type":"delete_task","id":1}]}

User: "I asked for treats; mark it down"
CURRENT_TODO_STATE.unfinished contains {"id":4, "title":"Ask for treats"}
→ {"say":"Marked 'Ask for treats' as done.","actions":[{"type":"complete_task","id":4}]}

Ambiguous:
→ {"say":"I found two similar items. Which one do you mean?","actions":[]}"""
        )

        val stateMessage = stateProvider?.invoke()?.let { snapshot ->
            OpenAIChatMessage(role = "system", content = "CURRENT_TODO_STATE: $snapshot")
        }

        val messages = mutableListOf<OpenAIChatMessage>()
        messages.add(systemInstruction)
        stateMessage?.let { messages.add(it) }
        messages.addAll(history.map {
            OpenAIChatMessage(
                role = it.sender.name.lowercase(),
                content = it.text
            )
        })

        val request = OpenAIRequest(
            model = "gpt-5-mini",
            messages = messages.takeLast(10) // Ensure we don't exceed token limits
        )
        lateinit var response: String
        try {
            Log.d("RealAssistantClient", "Request: ${json.encodeToString(request)}")
            response = client.post("https://api.openai.com/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            Log.d("RealAssistantClient", "Response: $response")

            val openAIResponse = json.decodeFromString<OpenAIResponse>(response)
            return Result.success(openAIResponse.choices.first().message.content)
        } catch (e: HttpRequestTimeoutException) {
            return Result.failure(Exception("Request timed out. Please check your internet connection and try again."))
        } catch (e: MissingFieldException) {
            return try {
                val errorResponse = json.decodeFromString<OpenAIErrorResponse>(response)
                Result.failure(Exception("API Error: ${errorResponse.error.message}"))
            } catch (_: Exception) {
                Result.failure(Exception("Failed to parse API response: ${e.message}"))
            }
        } catch (e: Exception) {
            return Result.failure(Exception("An unexpected error occurred: ${e.message}"))
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