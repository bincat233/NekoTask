package me.superbear.todolist.assistant

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import me.superbear.todolist.domain.entities.ChatMessage

/**
 * Seam between [me.superbear.todolist.ui.main.MainViewModel] and the concrete AI backend,
 * so Compose UI tests can inject a fake implementation instead of hitting a real LLM.
 */
interface ChatAgent {
    fun setApiKey(provider: LLMProvider, key: String)
    fun selectProvider(provider: LLMProvider)
    fun selectModelByName(name: String)
    fun buildExecutor(): PromptExecutor
    fun getCurrentModel(): LLModel

    suspend fun chat(
        userMessage: String,
        history: List<ChatMessage>,
        todoState: String,
        memoryContext: String = "",
        language: String = "en"
    ): Result<String>
}
