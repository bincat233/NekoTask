package me.superbear.todolist.assistant.search

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TavilySearchProviderTest {

    @Test
    fun parsesSuccessfulResponseWithOneBasedIds() = runBlocking {
        val mockEngine = MockEngine {
            respond(
                content = """{"results":[
                    {"title":"First","url":"https://a.example","content":"A content"},
                    {"title":"Second","url":"https://b.example","content":"B content"}
                ]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val provider = TavilySearchProvider(apiKey = "test-key", engineHttpClient = HttpClient(mockEngine))

        val result = provider.search("query", 5)

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(2, items.size)
        assertEquals(1, items[0].id)
        assertEquals("First", items[0].title)
        assertEquals("https://a.example", items[0].url)
        assertEquals("A content", items[0].content)
        assertEquals(2, items[1].id)
        assertEquals("Second", items[1].title)
    }

    @Test
    fun nonSuccessHttpResponseYieldsFailure() = runBlocking {
        val mockEngine = MockEngine {
            respond(
                content = "unauthorized",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val provider = TavilySearchProvider(apiKey = "bad-key", engineHttpClient = HttpClient(mockEngine))

        val result = provider.search("query", 5)

        assertTrue(result.isFailure)
    }

    @Test
    fun blankApiKeyShortCircuitsWithoutAnyNetworkCall() = runBlocking {
        var handlerCalled = false
        val mockEngine = MockEngine {
            handlerCalled = true
            respond(content = "{}", status = HttpStatusCode.OK)
        }
        val provider = TavilySearchProvider(apiKey = "", engineHttpClient = HttpClient(mockEngine))

        val result = provider.search("query", 5)

        assertTrue(result.isFailure)
        assertFalse(handlerCalled)
    }
}
