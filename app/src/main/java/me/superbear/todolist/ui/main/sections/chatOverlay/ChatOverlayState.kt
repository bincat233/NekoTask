package me.superbear.todolist.ui.main.sections.chatOverlay

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.superbear.todolist.domain.entities.ChatMessage

/**
 * Chat overlay section state - manages chat messages, overlay modes, and UI measurements
 */
data class ChatOverlayState(
    val messages: List<ChatMessage> = emptyList(),
    val peekMessages: List<ChatMessage> = emptyList(),
    val pinnedMessageIds: Set<String> = emptySet(),
    val chatOverlayMode: String = "peek", // "peek" or "fullscreen"
    val fabWidthDp: Dp = 0.dp,
    val imeVisible: Boolean = false
)
