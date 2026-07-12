package me.superbear.todolist.assistant

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.superbear.todolist.assistant.search.SearchProvider
import me.superbear.todolist.assistant.search.SearchResultItem

@Serializable
data class SearchResponse(
    val query: String,
    val results: List<SearchResultItem>,
    val error: String? = null
)

@LLMDescription("Tools for searching the web for current information not already in the todo list or memory")
class SearchToolSet(
    private val provider: SearchProvider
) : ToolSet {
    private val json = Json { encodeDefaults = true }

    @Tool
    @LLMDescription(
        "Search the web for a query. Returns a JSON object with results (title/url/content) or an error field."
    )
    suspend fun web_search(
        @LLMDescription("The search query") query: String,
        @LLMDescription("Max results, 1-10, default 5") maxResults: Int? = null
    ): String {
        val capped = (maxResults ?: 5).coerceIn(1, 10)
        return provider.search(query, capped).fold(
            onSuccess = { results -> json.encodeToString(SearchResponse(query, results)) },
            onFailure = { error -> json.encodeToString(SearchResponse(query, emptyList(), error.message ?: "search failed")) }
        )
    }
}
