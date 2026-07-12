package me.superbear.todolist.assistant.search

import ai.koog.prompt.llm.LLMProvider

/**
 * Which search mechanism the user has selected, as a lightweight, persistable identifier —
 * this is what flows through Settings/prefs/UI, analogous to how [LLMProvider] and the
 * selected-model name are plain identifiers rather than live objects.
 */
enum class SearchCapabilityKind {
    /** Tavily today; more [SearchProvider] backends can share this slot later without a new kind. */
    TAVILY,

    /** OpenAI's Responses API hosted `web_search` tool. Stubbed — see docs/AI_TASK_TOOLS.md "已知后续工作". */
    OPENAI_NATIVE
}

/**
 * Whether [this] kind can actually work given the currently selected chat LLM provider. Single
 * source of truth used both to filter the Settings picker and to defensively re-check at
 * agent-build time (persisted state can go stale if the user changes providers elsewhere without
 * revisiting search settings).
 */
fun SearchCapabilityKind.isAvailable(currentLlmProvider: LLMProvider): Boolean = when (this) {
    SearchCapabilityKind.TAVILY -> true
    SearchCapabilityKind.OPENAI_NATIVE -> currentLlmProvider == LLMProvider.OpenAI
}

/**
 * Execution-time representation used internally by
 * [me.superbear.todolist.assistant.TodoAgent] — resolved from a [SearchCapabilityKind] plus
 * whichever concrete [SearchProvider] instance the agent owns. [ExternalApi] providers all share
 * the same "call it, get a list back" [SearchProvider] contract; native-search mechanisms don't
 * fit that contract at all (they configure the LLM request itself rather than being called as a
 * tool), so each one is its own sealed variant instead of being forced into a shared interface.
 * Not persisted/passed through Settings directly — see [SearchCapabilityKind] for that.
 */
sealed interface SearchCapability {
    data class ExternalApi(val provider: SearchProvider) : SearchCapability
    data object OpenAiNativeSearch : SearchCapability

    // Future: AnthropicNativeSearch / GeminiNativeSearch, once those become selectable LLM providers.
}
