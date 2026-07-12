package me.superbear.todolist.assistant

import kotlinx.coroutines.flow.Flow
import me.superbear.todolist.domain.entities.ChatMessage

/**
 * Events emitted during a streaming AI chat session.
 */
sealed class ChatStreamEvent {
    data class TextDelta(val text: String) : ChatStreamEvent()
    data class ToolCallStarted(val toolName: String) : ChatStreamEvent()
    data object AgentCompleted : ChatStreamEvent()
    data class Error(val throwable: Throwable) : ChatStreamEvent()
}

/**
 * Seam between [me.superbear.todolist.ui.main.MainViewModel] and the concrete AI backend,
 * so Compose UI tests can inject a fake implementation instead of hitting a real LLM.
 */
interface ChatAgent {
    suspend fun chat(
        userMessage: String,
        history: List<ChatMessage>,
        todoState: String,
        memoryContext: String = "",
        language: String = "en"
    ): Result<String>

    fun chatStreaming(
        userMessage: String,
        history: List<ChatMessage>,
        todoState: String,
        memoryContext: String = "",
        language: String = "en"
    ): Flow<ChatStreamEvent>
}
