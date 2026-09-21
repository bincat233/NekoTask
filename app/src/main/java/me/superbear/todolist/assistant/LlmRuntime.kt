package me.superbear.todolist.assistant

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * Runtime seam for model/provider configuration and executor construction.
 *
 * Chat callers should depend on [ChatAgent]. Settings and subtask division use this seam
 * because they need to mutate or reuse the current LLM runtime.
 */
interface LlmRuntime {
    fun setApiKey(provider: AssistantProvider, key: String)
    fun setCustomBaseUrl(baseUrl: String)
    fun selectProvider(provider: AssistantProvider)
    fun selectModelByName(name: String)
    fun buildExecutor(): PromptExecutor
    fun getCurrentModel(): LLModel
}
