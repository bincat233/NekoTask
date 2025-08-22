package me.superbear.todolist

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

interface AssistantClient {
    suspend fun send(userText: String, history: List<ChatMessage>): Result<String>
}

class MockAssistantClient : AssistantClient {
    override suspend fun send(userText: String, history: List<ChatMessage>): Result<String> {
        delay(Random.nextLong(1000, 5000))
        return Result.success(userText) // echo
    }
}

class RealAssistantClient : AssistantClient {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    override suspend fun send(userText: String, history: List<ChatMessage>): Result<String> {
        return try {
            val messages = history.map {
                Message(role = it.sender.name.lowercase(), content = it.text)
            } + Message(role = "user", content = userText)

            val response: HttpResponse = client.post("https://api.openai.com/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                setBody(
                    OpenAIRequest(
                        model = "gpt-5-mini",
                        messages = messages
                    )
                )
            }
            val responseBody = response.bodyAsText()
            val openAIResponse = Json.decodeFromString<OpenAIResponse>(responseBody)
            val assistantResponse = openAIResponse.choices.first().message.content
            Result.success(assistantResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<Message>
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: Message
)