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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.domain.entities.ChatMessage
import java.net.UnknownHostException

interface AssistantClient {
    suspend fun sendChat(message: String, history: List<ChatMessage>, memoryContext: String = ""): Result<AssistantEnvelope>
}

interface TextAssistantClient {
    suspend fun sendText(prompt: String): Result<String>
}

class MockAssistantClient : AssistantClient, TextAssistantClient {
    private val chatResponses = listOf(
        AssistantEnvelope(say = "I can help. What's the task?", actions = emptyList()),
        AssistantEnvelope(
            say = "Added to your list.",
            actions = listOf(AssistantAction.AddTask(title = "Buy milk", content = "2%"))
        )
    )

    private val subtaskResponse = """
        {
          "reasoning": "Break into simple, sequential steps.",
          "subtasks": [
            {"title":"Gather requirements","content":"Clarify scope and constraints","priority":"MEDIUM","estimatedOrder":1,"dependencies":[]},
            {"title":"Draft plan","content":"Outline key steps and timeline","priority":"MEDIUM","estimatedOrder":2,"dependencies":[0]},
            {"title":"Execute tasks","content":"Complete the planned work","priority":"HIGH","estimatedOrder":3,"dependencies":[1]}
          ]
        }
    """.trimIndent()

    override suspend fun sendChat(message: String, history: List<ChatMessage>, memoryContext: String): Result<AssistantEnvelope> {
        delay(600)
        return Result.success(chatResponses[Random.nextInt(chatResponses.size)])
    }

    override suspend fun sendText(prompt: String): Result<String> {
        delay(600)
        return Result.success(subtaskResponse)
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

class RealAssistantClient : AssistantClient, TextAssistantClient {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false  // Use compact format for transmission
        explicitNulls = false
        encodeDefaults = true  // Ensure default values like type="function" are serialized
        isLenient = true
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
            "- ${m.role}:\n" + prettyEmbeddedJson(m.content ?: "")
        }
        Log.d("RealAssistantClient", "Request message contents (pretty):\n$prettyContents")
    }

    private fun logResponse(response: String, assistantContent: String?) {
        Log.d("RealAssistantClient", "Response (pretty): ${prettyWholeJson(response)}")
        assistantContent?.let {
            Log.d("RealAssistantClient", "Response content (pretty):\n${prettyEmbeddedJson(it)}")
        }
    }

    private fun parseChatResponse(response: String): Result<AssistantEnvelope> {
        return try {
            val openAIResponse = json.decodeFromString<OpenAIResponse>(response)
            val message = openAIResponse.choices.firstOrNull()?.message
            val assistantContent = message?.content
            logResponse(response, assistantContent)
            val actions = message?.toolCalls?.mapNotNull(::mapToolCall).orEmpty()
            Result.success(AssistantEnvelope(say = assistantContent, actions = actions))
        } catch (e: MissingFieldException) {
            handleParsingError(response, e).map { AssistantEnvelope(say = it, actions = emptyList()) }
        }
    }

    private fun parseTextResponse(response: String): Result<String> {
        return try {
            val openAIResponse = json.decodeFromString<OpenAIResponse>(response)
            val assistantContent = openAIResponse.choices.firstOrNull()?.message?.content
            logResponse(response, assistantContent)
            if (assistantContent == null) {
                Result.failure(Exception("Empty response content from model."))
            } else {
                Result.success(assistantContent)
            }
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

    override suspend fun sendChat(message: String, history: List<ChatMessage>, memoryContext: String): Result<AssistantEnvelope> {
        if (apiKey.isBlank() || apiKey.equals("null", ignoreCase = true)) {
            return Result.failure(Exception("OpenAI API key is missing. Please add it to your local.properties file."))
        }

        val systemInstruction = OpenAIChatMessage(
            role = "system",
            content = """You are a helpful assistant for a to-do list app.
Use tools to create, update, delete, or complete tasks. When you use tools, keep a short user-facing reply in content.
If the request is ambiguous, ask a clarifying question and do not call tools.
You will receive CURRENT_TODO_STATE as a system message. Use ids from that state when referencing existing tasks."""
        )

        val memoryMessage = memoryContext.takeIf { it.isNotBlank() }?.let {
            OpenAIChatMessage(role = "system", content = it)
        }

        val stateMessage = stateProvider?.invoke()?.let { snapshot ->
            OpenAIChatMessage(role = "system", content = "CURRENT_TODO_STATE: $snapshot")
        }

        val historyMessages = history.map {
            OpenAIChatMessage(
                role = it.sender.name.lowercase(),
                content = it.text
            )
        }

        val maxTotal = 10
        val reserved = 3
        val limitedHistory = historyMessages.takeLast(maxTotal - reserved)

        val messages = mutableListOf<OpenAIChatMessage>().apply {
            add(systemInstruction)
            memoryMessage?.let { add(it) }
            stateMessage?.let { add(it) }
            addAll(limitedHistory)
        }

        val request = OpenAIRequest(
            model = "gpt-5-mini",
            messages = messages,
            tools = tools,
            toolChoice = JsonPrimitive("auto")
        )

        return try {
            logRequest(request, messages)

            val response = client.post("${baseUrl.trimEnd('/')}/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<String>()

            parseChatResponse(response)
        } catch (e: HttpRequestTimeoutException) {
            Result.failure(Exception("Request timed out. Please check your internet connection and try again."))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Unable to resolve host. Check your DNS/proxy or network connectivity."))
        } catch (e: Exception) {
            Result.failure(Exception("An unexpected error occurred: ${e.message}"))
        }
    }

    override suspend fun sendText(prompt: String): Result<String> {
        if (apiKey.isBlank() || apiKey.equals("null", ignoreCase = true)) {
            return Result.failure(Exception("OpenAI API key is missing. Please add it to your local.properties file."))
        }

        val messages = listOf(
            OpenAIChatMessage(role = "user", content = prompt)
        )

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

            parseTextResponse(response)
        } catch (e: HttpRequestTimeoutException) {
            Result.failure(Exception("Request timed out. Please check your internet connection and try again."))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Unable to resolve host. Check your DNS/proxy or network connectivity."))
        } catch (e: Exception) {
            Result.failure(Exception("An unexpected error occurred: ${e.message}"))
        }
    }

    private fun mapToolCall(call: OpenAIToolCall): AssistantAction? {
        return when (call.function.name) {
            "add_task" -> decodeArgs<AddTaskArgs>(call.function.arguments)?.let { args ->
                val title = args.title?.trim().orEmpty()
                if (title.isBlank()) {
                    Log.e("RealAssistantClient", "add_task missing title: ${call.function.arguments}")
                    null
                } else {
                    AssistantAction.AddTask(
                        title = title,
                        content = args.content?.trim()?.takeIf { it.isNotEmpty() },
                        dueAtIso = args.dueAt?.trim()?.takeIf { it.isNotEmpty() },
                        priority = args.priority?.trim()?.takeIf { it.isNotEmpty() },
                        parentId = args.parentId,
                        orderInParent = args.orderInParent
                    )
                }
            }
            "update_task" -> decodeArgs<UpdateTaskArgs>(call.function.arguments)?.let { args ->
                val id = args.id
                if (id == null) {
                    Log.e("RealAssistantClient", "update_task missing id: ${call.function.arguments}")
                    null
                } else {
                    AssistantAction.UpdateTask(
                        id = id,
                        title = args.title?.trim()?.takeIf { it.isNotEmpty() },
                        content = args.content?.trim()?.takeIf { it.isNotEmpty() },
                        dueAtIso = args.dueAt?.trim()?.takeIf { it.isNotEmpty() },
                        priority = args.priority?.trim()?.takeIf { it.isNotEmpty() },
                        parentId = args.parentId,
                        orderInParent = args.orderInParent
                    )
                }
            }
            "delete_task" -> decodeArgs<IdArgs>(call.function.arguments)?.let { args ->
                args.id?.let { AssistantAction.DeleteTask(id = it) }
            }
            "complete_task" -> decodeArgs<IdArgs>(call.function.arguments)?.let { args ->
                args.id?.let { AssistantAction.CompleteTask(id = it) }
            }
            else -> {
                Log.e("RealAssistantClient", "Unknown tool call: ${call.function.name}")
                null
            }
        }
    }

    private inline fun <reified T> decodeArgs(arguments: String): T? {
        return runCatching { json.decodeFromString<T>(arguments) }
            .onFailure { Log.e("RealAssistantClient", "Failed to parse tool arguments: $arguments", it) }
            .getOrNull()
    }

    private val tools: List<OpenAITool> by lazy {
        listOf(
            OpenAITool(
                function = OpenAIFunctionDef(
                    name = "add_task",
                    description = "Add a new task or subtask.",
                    parameters = buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("properties", buildJsonObject {
                            put("title", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("Task title"))
                            })
                            put("content", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("Task notes"))
                            })
                            put("dueAt", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("ISO-8601 due date"))
                            })
                            put("priority", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("LOW, MEDIUM, HIGH, or DEFAULT"))
                            })
                            put("parentId", buildJsonObject {
                                put("type", JsonPrimitive("integer"))
                                put("description", JsonPrimitive("Parent task id for a subtask"))
                            })
                            put("orderInParent", buildJsonObject {
                                put("type", JsonPrimitive("integer"))
                                put("description", JsonPrimitive("Optional order in parent list"))
                            })
                        })
                        put("required", buildJsonArray {
                            add(JsonPrimitive("title"))
                        })
                    }
                )
            ),
            OpenAITool(
                function = OpenAIFunctionDef(
                    name = "update_task",
                    description = "Update fields of an existing task.",
                    parameters = buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("properties", buildJsonObject {
                            put("id", buildJsonObject {
                                put("type", JsonPrimitive("integer"))
                                put("description", JsonPrimitive("Task id"))
                            })
                            put("title", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("New title"))
                            })
                            put("content", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("New notes"))
                            })
                            put("dueAt", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("ISO-8601 due date"))
                            })
                            put("priority", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("LOW, MEDIUM, HIGH, or DEFAULT"))
                            })
                            put("parentId", buildJsonObject {
                                put("type", JsonPrimitive("integer"))
                                put("description", JsonPrimitive("New parent id for reparenting"))
                            })
                            put("orderInParent", buildJsonObject {
                                put("type", JsonPrimitive("integer"))
                                put("description", JsonPrimitive("Optional order in parent list"))
                            })
                        })
                        put("required", buildJsonArray {
                            add(JsonPrimitive("id"))
                        })
                    }
                )
            ),
            OpenAITool(
                function = OpenAIFunctionDef(
                    name = "delete_task",
                    description = "Delete a task by id.",
                    parameters = buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("properties", buildJsonObject {
                            put("id", buildJsonObject {
                                put("type", JsonPrimitive("integer"))
                                put("description", JsonPrimitive("Task id"))
                            })
                        })
                        put("required", buildJsonArray {
                            add(JsonPrimitive("id"))
                        })
                    }
                )
            ),
            OpenAITool(
                function = OpenAIFunctionDef(
                    name = "complete_task",
                    description = "Mark a task as completed by id.",
                    parameters = buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("properties", buildJsonObject {
                            put("id", buildJsonObject {
                                put("type", JsonPrimitive("integer"))
                                put("description", JsonPrimitive("Task id"))
                            })
                        })
                        put("required", buildJsonArray {
                            add(JsonPrimitive("id"))
                        })
                    }
                )
            )
        )
    }
}

@Serializable
private data class AddTaskArgs(
    val title: String? = null,
    val content: String? = null,
    @SerialName("dueAt") val dueAt: String? = null,
    val priority: String? = null,
    val parentId: Long? = null,
    val orderInParent: Long? = null
)

@Serializable
private data class UpdateTaskArgs(
    val id: Long? = null,
    val title: String? = null,
    val content: String? = null,
    @SerialName("dueAt") val dueAt: String? = null,
    val priority: String? = null,
    val parentId: Long? = null,
    val orderInParent: Long? = null
)

@Serializable
private data class IdArgs(
    val id: Long? = null
)

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIChatMessage>,
    val tools: List<OpenAITool>? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null
)

@Serializable
data class OpenAITool(
    val type: String = "function",
    val function: OpenAIFunctionDef
)

@Serializable
data class OpenAIFunctionDef(
    val name: String,
    val description: String,
    val parameters: JsonObject
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
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAIToolCall>? = null
)

@Serializable
data class OpenAIToolCall(
    val id: String? = null,
    val type: String,
    val function: OpenAIFunctionCall
)

@Serializable
data class OpenAIFunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class OpenAIErrorResponse(
    val error: OpenAIError
)

@Serializable
data class OpenAIError(
    val message: String
)
