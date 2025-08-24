package me.superbear.todolist.ui.main.sections.chatOverlay

import androidx.compose.ui.unit.Dp

/**
 * Chat overlay domain events
 */
sealed class ChatOverlayEvent {
    data class SendMessage(val message: String) : ChatOverlayEvent()
    data class FabMeasured(val widthDp: Dp) : ChatOverlayEvent()
    data class DismissPeekMessage(val id: String) : ChatOverlayEvent()
    data class PinMessage(val id: String) : ChatOverlayEvent()
    data class UnpinMessage(val id: String) : ChatOverlayEvent()
    data class SetChatOverlayMode(val mode: String) : ChatOverlayEvent()
    object EnterFullscreenChat : ChatOverlayEvent()
    data class SetImeVisible(val visible: Boolean) : ChatOverlayEvent()
}
