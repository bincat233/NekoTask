package me.superbear.todolist.assistant.search

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [SearchProvider] backed by Tavily's REST API (https://api.tavily.com/search).
 * Auth is a Bearer token in the Authorization header, not an api_key body field.
 */
class TavilySearchProvider(
    private var apiKey: String = "",
    engineHttpClient: HttpClient = HttpClient(CIO)
) : SearchProvider {

    private val httpClient = engineHttpClient.config {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
        defaultRequest {
            url("https://api.tavily.com/")
            contentType(ContentType.Application.Json)
        }
    }

    fun setApiKey(key: String) {
        apiKey = key
    }

    override suspend fun search(query: String, maxResults: Int): Result<List<SearchResultItem>> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Tavily API key not configured"))
        }
        return try {
            val response = httpClient.post("search") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(TavilySearchRequest(query = query, maxResults = maxResults))
            }
            if (!response.status.isSuccess()) {
                return Result.failure(TavilyHttpException(response))
            }
            val body: TavilySearchResponse = response.body()
            Result.success(
                body.results.mapIndexed { index, result ->
                    SearchResultItem(id = index + 1, title = result.title, url = result.url, content = result.content)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class TavilyHttpException(response: HttpResponse) : Exception("Tavily error: HTTP ${response.status.value}")

@Serializable
private data class TavilySearchRequest(
    val query: String,
    @SerialName("max_results") val maxResults: Int
)

@Serializable
private data class TavilySearchResponse(val results: List<TavilyResultItem>)

@Serializable
private data class TavilyResultItem(val title: String, val url: String, val content: String)
