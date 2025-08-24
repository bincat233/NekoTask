package me.superbear.todolist.ui.main.sections.chatOverlay

import me.superbear.todolist.domain.entities.ChatMessage

/**
 * Pure function reducer for chat overlay state updates
 */
object ChatOverlayReducer {
    
    fun reduce(state: ChatOverlayState, event: ChatOverlayEvent): ChatOverlayState {
        return when (event) {
            is ChatOverlayEvent.SendMessage -> {
                // The actual message sending is handled by ViewModel/AssistantController
                // Reducer just returns current state
                state
            }
            is ChatOverlayEvent.FabMeasured -> {
                state.copy(fabWidthDp = event.widthDp)
            }
            is ChatOverlayEvent.DismissPeekMessage -> {
                state.copy(
                    peekMessages = state.peekMessages.filter { it.id != event.id }
                )
            }
            is ChatOverlayEvent.PinMessage -> {
                state.copy(
                    pinnedMessageIds = state.pinnedMessageIds + event.id
                )
            }
            is ChatOverlayEvent.UnpinMessage -> {
                state.copy(
                    pinnedMessageIds = state.pinnedMessageIds - event.id
                )
            }
            is ChatOverlayEvent.SetChatOverlayMode -> {
                state.copy(chatOverlayMode = event.mode)
            }
            is ChatOverlayEvent.EnterFullscreenChat -> {
                state.copy(chatOverlayMode = "fullscreen")
            }
            is ChatOverlayEvent.SetImeVisible -> {
                state.copy(imeVisible = event.visible)
            }
        }
    }
    
    /**
     * Add messages to both full and peek message lists
     */
    fun addMessages(state: ChatOverlayState, vararg messages: ChatMessage): ChatOverlayState {
        val added = messages.toList()
        return state.copy(
            messages = state.messages + added,
            peekMessages = state.peekMessages + added
        )
    }
    
    /**
     * Replace a message in both full and peek message lists
     */
    fun replaceMessage(state: ChatOverlayState, oldMessageId: String, newMessage: ChatMessage): ChatOverlayState {
        val newMessages = state.messages.map { msg ->
            if (msg.id == oldMessageId) newMessage else msg
        }
        val newPeekMessages = state.peekMessages.map { msg ->
            if (msg.id == oldMessageId) newMessage else msg
        }
        return state.copy(messages = newMessages, peekMessages = newPeekMessages)
    }
    
    /**
     * Remove a message from both full and peek message lists
     */
    fun removeMessage(state: ChatOverlayState, messageId: String): ChatOverlayState {
        return state.copy(
            messages = state.messages.filter { it.id != messageId },
            peekMessages = state.peekMessages.filter { it.id != messageId }
        )
    }
}
