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
                    "You must respond with a SINGLE compact JSON object. " +
                    "The JSON object must have two keys: 'say' and 'actions'. " +
                    "The 'say' value is a string that you want to say to the user. " +
                    "The 'actions' value is an array of actions to take. " +
                    "Valid actions are: " +
                    "{\"type\":\"add_task\",\"title\":\"string\",\"notes\":\"string?\",\"dueAt\":\"ISO-8601?\",\"priority\":\"LOW|MEDIUM|HIGH|?\"}. " +
                    "If there are no actions, return an empty array. " +
                    "Do not include any extra prose or code fences in your response."
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