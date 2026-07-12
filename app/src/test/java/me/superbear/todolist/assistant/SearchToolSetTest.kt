package me.superbear.todolist.assistant

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.superbear.todolist.assistant.search.SearchProvider
import me.superbear.todolist.assistant.search.SearchResultItem
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeSearchProvider(
    private val result: Result<List<SearchResultItem>>
) : SearchProvider {
    var lastMaxResults: Int? = null
        private set

    override suspend fun search(query: String, maxResults: Int): Result<List<SearchResultItem>> {
        lastMaxResults = maxResults
        return result
    }
}

class SearchToolSetTest {

    @Test
    fun successfulSearchReturnsPopulatedResultsAndNoError() = runBlocking {
        val items = listOf(SearchResultItem(id = 1, title = "T", url = "https://x.example", content = "C"))
        val toolSet = SearchToolSet(FakeSearchProvider(Result.success(items)))

        val response = Json.parseToJsonElement(toolSet.web_search("query")).jsonObject

        assertEquals("query", response["query"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, response["error"])
        val results = response["results"]!!.jsonArray
        assertEquals(1, results.size)
        assertEquals("T", results[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun failedSearchReturnsEmptyResultsAndErrorMessage() = runBlocking {
        val toolSet = SearchToolSet(FakeSearchProvider(Result.failure(IllegalStateException("boom"))))

        val response = Json.parseToJsonElement(toolSet.web_search("query")).jsonObject

        assertEquals(0, response["results"]!!.jsonArray.size)
        assertEquals("boom", response["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun maxResultsIsClampedAndDefaultsToFiveWhenNull() = runBlocking {
        val provider = FakeSearchProvider(Result.success(emptyList()))
        val toolSet = SearchToolSet(provider)

        toolSet.web_search("q", maxResults = null)
        assertEquals(5, provider.lastMaxResults)

        toolSet.web_search("q", maxResults = 0)
        assertEquals(1, provider.lastMaxResults)

        toolSet.web_search("q", maxResults = 100)
        assertEquals(10, provider.lastMaxResults)

        toolSet.web_search("q", maxResults = 7)
        assertEquals(7, provider.lastMaxResults)
    }
}
