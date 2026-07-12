package me.superbear.todolist.assistant

import me.superbear.todolist.assistant.search.SearchCapabilityKind

/**
 * Runtime seam for search configuration, mirroring [LlmRuntime]'s split from [ChatAgent]: chat
 * callers don't need this, only Settings does.
 */
interface SearchRuntime {
    fun setSearchCapability(kind: SearchCapabilityKind)

    /** Only meaningful for external-API providers (e.g. Tavily); native search reuses the LLM provider's own key. */
    fun setSearchApiKey(key: String)
}
