package me.superbear.todolist

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

interface AssistantClient {
    suspend fun send(message: String, history: List<ChatMessage>): Result<String>
}

class MockAssistantClient : AssistantClient {
    private val responses = listOf(
        "I can help with that. What is the task?",
        "I've added it to your list.",
        "Here are some tasks you can add: [{\"action\": \"AddTask\", \"title\": \"Buy milk\", \"notes\": \"Get 2% milk\"}]"
    )

    override suspend fun send(message: String, history: List<ChatMessage>): Result<String> {
        delay(1000)
        return Result.success(responses[Random.nextInt(responses.size)])
    }
}

class RealAssistantClient : AssistantClient {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val apiKey: String = BuildConfig.OPENAI_API_KEY

    override suspend fun send(message: String, history: List<ChatMessage>): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("OpenAI API key is missing. Please add it to your local.properties file."))
        }
        val systemMessage = OpenAIChatMessage(
            role = "system",
            content = "You are a helpful assistant that helps people manage their to-do lists. " +
                    "You can add, remove, and update tasks. " +
                    "When a user asks you to add a task, you should respond with a JSON object that contains the action to take. " +
                    "For example, if the user says 'add a task to buy milk', you should respond with " +
                    "'{\"action\": \"AddTask\", \"title\": \"Buy milk\", \"notes\": \"Get 2% milk\"}'"
        )
        val messages = (listOf(systemMessage) + history.map {
            OpenAIChatMessage(
                role = it.sender.name.lowercase(),
                content = it.text
            )
        }).takeLast(10)

        val request = OpenAIRequest(
            model = "gpt-5-mini",
            messages = messages
        )
        try {
            val response: String = client.post("https://api.openai.com/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            Log.d("RealAssistantClient", "OpenAI response: $response")
            val openAIResponse = json.decodeFromString<OpenAIResponse>(response)
            return Result.success(openAIResponse.choices.first().message.content)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error sending message", e)
            return Result.failure(e)
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