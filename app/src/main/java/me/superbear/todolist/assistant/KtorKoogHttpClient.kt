package me.superbear.todolist.assistant

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.mergeHeaders
import ai.koog.utils.io.SuitableForIO
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

class KtorKoogHttpClient(
    override val clientName: String,
    val ktorClient: HttpClient
) : KoogHttpClient {

    private fun HttpResponse.safeUrl(): String = call.request.url.toString().substringBefore('?')

    private fun String.forLog(maxLength: Int = 2_000): String =
        replace(Regex("[\\r\\n]+"), " ").let { text ->
            if (text.length <= maxLength) text else "${text.take(maxLength)}…(${text.length} chars)"
        }

    private suspend fun <R : Any> processResponse(response: HttpResponse, responseType: KClass<R>): R {
        if (response.status.isSuccess()) {
            Log.d(TAG, "$clientName HTTP ${response.status.value} ${response.safeUrl()}")
            if (responseType == String::class) {
                @Suppress("UNCHECKED_CAST")
                return response.bodyAsText() as R
            }
            return response.body(TypeInfo(responseType))
        }
        val errorBody = response.bodyAsText()
        Log.e(
            TAG,
            "$clientName HTTP ${response.status.value} ${response.safeUrl()} body=${errorBody.forLog()}"
        )
        throw KoogHttpClientException(
            clientName = clientName,
            statusCode = response.status.value,
            errorBody = errorBody,
        )
    }

    override suspend fun <R : Any> get(
        path: String,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val response = ktorClient.get(path) {
            parameters.forEach { (key, value) -> parameter(key, value) }
            headers.forEach { (name, value) ->
                if (name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                    contentType(ContentType.parse(value))
                } else {
                    header(name, value)
                }
            }
        }
        processResponse(response, responseType)
    }

    override suspend fun <T : Any, R : Any> post(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        responseType: KClass<R>,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ): R = withContext(Dispatchers.SuitableForIO) {
        val response = ktorClient.post(path) {
            if (requestBodyType == String::class) {
                @Suppress("UNCHECKED_CAST")
                setBody(requestBody as String)
            } else {
                setBody(requestBody, TypeInfo(requestBodyType))
            }
            parameters.forEach { (key, value) -> parameter(key, value) }
            headers.forEach { (name, value) ->
                if (name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                    contentType(ContentType.parse(value))
                } else {
                    header(name, value)
                }
            }
        }
        processResponse(response, responseType)
    }

    override fun <T : Any, R : Any, O : Any> sse(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        dataFilter: (String?) -> Boolean,
        decodeStreamingResponse: (String) -> R,
        processStreamingChunk: (R) -> O?,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): Flow<O> = flow {
        Log.d(TAG, "$clientName opening SSE path=$path")
        try {
            ktorClient.preparePost(path) {
                parameters.forEach { (key, value) -> parameter(key, value) }
                val mergedHeaders = mergeHeaders(
                    mapOf(
                        HttpHeaders.Accept to ContentType.Text.EventStream.toString(),
                        HttpHeaders.CacheControl to "no-cache",
                        HttpHeaders.Connection to "keep-alive",
                    ),
                    headers,
                )
                mergedHeaders.forEach { (name, value) -> header(name, value) }
                if (requestBodyType == String::class) {
                    @Suppress("UNCHECKED_CAST")
                    setBody(requestBody as String)
                } else {
                    setBody(requestBody, TypeInfo(requestBodyType))
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    Log.e(
                        TAG,
                        "$clientName SSE HTTP ${response.status.value} ${response.safeUrl()} " +
                            "body=${errorBody.forLog()}"
                    )
                    throw KoogHttpClientException(
                        clientName = clientName,
                        statusCode = response.status.value,
                        errorBody = errorBody,
                    )
                }
                Log.d(TAG, "$clientName SSE HTTP ${response.status.value} ${response.safeUrl()}")
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) continue
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        if (dataFilter(data)) {
                            val event = decodeStreamingResponse(data)
                            processStreamingChunk(event)?.let { emit(it) }
                        }
                    }
                }
            }
        } catch (e: KoogHttpClientException) {
            Log.e(TAG, "$clientName SSE request failed path=$path: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$clientName SSE transport/decode failed path=$path: ${e.message}", e)
            throw KoogHttpClientException(
                clientName = clientName,
                message = "SSE exception: ${e.message}",
                cause = e,
            )
        }
    }

    override fun <T : Any> lines(
        path: String,
        requestBody: T,
        requestBodyType: KClass<T>,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ): Flow<String> = flow {
        Log.d(TAG, "$clientName opening lines stream path=$path")
        try {
            ktorClient.preparePost(path) {
                parameters.forEach { (key, value) -> parameter(key, value) }
                headers.forEach { (name, value) -> header(name, value) }
                if (requestBodyType == String::class) {
                    @Suppress("UNCHECKED_CAST")
                    setBody(requestBody as String)
                } else {
                    setBody(requestBody, TypeInfo(requestBodyType))
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    Log.e(
                        TAG,
                        "$clientName lines HTTP ${response.status.value} ${response.safeUrl()} " +
                            "body=${errorBody.forLog()}"
                    )
                    throw KoogHttpClientException(
                        clientName = clientName,
                        statusCode = response.status.value,
                        errorBody = errorBody,
                    )
                }
                Log.d(TAG, "$clientName lines HTTP ${response.status.value} ${response.safeUrl()}")
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) continue
                    emit(line)
                }
            }
        } catch (e: KoogHttpClientException) {
            Log.e(TAG, "$clientName lines request failed path=$path: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$clientName lines transport/decode failed path=$path: ${e.message}", e)
            throw KoogHttpClientException(
                clientName = clientName,
                message = "Lines stream exception: ${e.message}",
                cause = e,
            )
        }
    }

    override fun close() {
        Log.d(TAG, "$clientName closing HTTP client")
        ktorClient.close()
    }

    class Factory(
        private val baseClient: HttpClient = HttpClient()
    ) : KoogHttpClient.Factory {
        override fun create(
            clientName: String,
            baseUrl: String,
            headers: Map<String, String>,
            queryParameters: Map<String, String>,
            requestTimeoutMillis: Long,
            connectTimeoutMillis: Long,
            socketTimeoutMillis: Long,
            json: Json
        ): KoogHttpClient {
            Log.d(
                TAG,
                "Creating $clientName baseUrl=${baseUrl.substringBefore('?').trimEnd('/')} " +
                    "authConfigured=${headers[HttpHeaders.Authorization]?.isNotBlank() == true} " +
                    "timeouts=${requestTimeoutMillis}/${connectTimeoutMillis}/${socketTimeoutMillis}ms"
            )
            val ktorClient = baseClient.config {
                install(ContentNegotiation) { json(json = json) }
                install(HttpTimeout) {
                    this.requestTimeoutMillis = requestTimeoutMillis
                    this.connectTimeoutMillis = connectTimeoutMillis
                    this.socketTimeoutMillis = socketTimeoutMillis
                }
                defaultRequest {
                    url(baseUrl)
                    contentType(ContentType.Application.Json)
                    headers.forEach { (name, value) -> header(name, value) }
                    queryParameters.forEach { (name, value) ->
                        url.parameters.append(name, value)
                    }
                }
            }
            return KtorKoogHttpClient(clientName, ktorClient)
        }
    }

    companion object {
        private const val TAG = "LlmHttp"
    }
}
