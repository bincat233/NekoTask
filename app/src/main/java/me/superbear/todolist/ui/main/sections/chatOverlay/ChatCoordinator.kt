package me.superbear.todolist.ui.main.sections.chatOverlay

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import me.superbear.todolist.R
import me.superbear.todolist.assistant.ChatAgent
import me.superbear.todolist.assistant.ChatStreamEvent
import me.superbear.todolist.data.repository.LongTermMemoryRepository
import me.superbear.todolist.domain.entities.ChatMessage
import me.superbear.todolist.domain.entities.MessageStatus
import me.superbear.todolist.domain.entities.Sender
import me.superbear.todolist.ui.main.sections.tasks.TaskListCoordinator
import java.util.Locale

/**
 * Owns chat-overlay state, the "should the assistant auto-execute actions" flag, and the
 * send/receive orchestration against the shared [ChatAgent].
 */
class ChatCoordinator(
    private val todoAgent: ChatAgent,
    private val longTermMemoryRepository: LongTermMemoryRepository,
    private val taskListCoordinator: TaskListCoordinator,
    private val application: Application,
    private val viewModelScope: CoroutineScope
) {
    private val _state = MutableStateFlow(ChatOverlayState())
    val state: StateFlow<ChatOverlayState> = _state.asStateFlow()

    fun handleEvent(event: ChatOverlayEvent) {
        when (event) {
            is ChatOverlayEvent.SendMessage -> handleSendChat(event.message)
            else -> _state.update { ChatOverlayReducer.reduce(it, event) }
        }
    }

    private fun handleSendChat(message: String) {
        val userMessage = ChatMessage(
            text = message, sender = Sender.User,
            timestamp = Clock.System.now(), status = MessageStatus.Sent
        )
        val assistantMessage = ChatMessage(
            sender = Sender.Assistant, text = "",
            timestamp = Clock.System.now(), status = MessageStatus.Sending,
            replyToId = userMessage.id
        )

        _state.update { ChatOverlayReducer.addMessages(it, userMessage, assistantMessage) }

        viewModelScope.launch {
            sendToAssistant(message, assistantMessage)
        }
    }

    private suspend fun sendToAssistant(message: String, placeholderMessage: ChatMessage) {
        val memoryContext = longTermMemoryRepository.getMemoryContextForAI()
        val todoState = taskListCoordinator.buildCurrentTodoStateSnapshot()

        var fullText = ""

        todoAgent.chatStreaming(
            userMessage = message,
            history = _state.value.messages,
            todoState = todoState,
            memoryContext = memoryContext,
            language = Locale.getDefault().language
        ).collect { event ->
            when (event) {
                is ChatStreamEvent.TextDelta -> {
                    fullText += event.text
                    val updatedMessage = placeholderMessage.copy(
                        text = fullText,
                        status = MessageStatus.Sending
                    )
                    _state.update { ChatOverlayReducer.replaceMessage(it, placeholderMessage.id, updatedMessage) }
                }
                is ChatStreamEvent.AgentCompleted -> {
                    if (fullText.isNotBlank()) {
                        val finalMessage = placeholderMessage.copy(
                            text = fullText,
                            status = MessageStatus.Sent
                        )
                        _state.update { ChatOverlayReducer.replaceMessage(it, placeholderMessage.id, finalMessage) }
                    } else {
                        // If no text was produced (e.g. only tool calls), remove the placeholder
                        _state.update { ChatOverlayReducer.removeMessage(it, placeholderMessage.id) }
                    }
                }
                is ChatStreamEvent.Error -> {
                    handleAssistantError(event.throwable, placeholderMessage)
                }
                else -> Unit
            }
        }
    }

    private fun handleAssistantError(error: Throwable, placeholderMessage: ChatMessage) {
        Log.e("ChatCoordinator", "Error sending message", error)
        val errorMessage = placeholderMessage.copy(
            text = application.getString(R.string.error_fetch_response),
            status = MessageStatus.Failed
        )
        _state.update { ChatOverlayReducer.replaceMessage(it, placeholderMessage.id, errorMessage) }
    }
}
