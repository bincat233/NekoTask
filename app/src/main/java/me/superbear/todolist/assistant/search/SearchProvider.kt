package me.superbear.todolist.assistant.search

import kotlinx.serialization.Serializable

/** One search hit. [id] is a 1-based position in the result list, used so the model can cite sources by number. */
@Serializable
data class SearchResultItem(
    val id: Int,
    val title: String,
    val url: String,
    val content: String
)

/** A pluggable web-search backend. Each implementation normalizes its provider's response into [SearchResultItem]. */
interface SearchProvider {
    suspend fun search(query: String, maxResults: Int): Result<List<SearchResultItem>>
}
