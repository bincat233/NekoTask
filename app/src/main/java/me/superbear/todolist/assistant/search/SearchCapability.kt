package me.superbear.todolist.assistant.search

/**
 * Which search mechanism the user has selected, as a lightweight, persistable identifier —
 * this is what flows through Settings/prefs/UI, analogous to how [ai.koog.prompt.llm.LLMProvider]
 * and the selected-model name are plain identifiers rather than live objects.
 */
enum class SearchCapabilityKind {
    /** Tavily today; more [SearchProvider] backends can share this slot later without a new kind. */
    TAVILY
}
