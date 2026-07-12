package me.superbear.todolist.assistant

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider

/**
 * Runtime seam for model/provider configuration and executor construction.
 *
 * Chat callers should depend on [ChatAgent]. Settings and subtask division use this seam
 * because they need to mutate or reuse the current LLM runtime.
 */
interface LlmRuntime {
    fun setApiKey(provider: LLMProvider, key: String)
    fun selectProvider(provider: LLMProvider)
    fun selectModelByName(name: String)
    fun buildExecutor(): PromptExecutor
    fun getCurrentModel(): LLModel
}
