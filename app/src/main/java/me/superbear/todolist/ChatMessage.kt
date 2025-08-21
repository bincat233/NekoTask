package me.superbear.todolist

import java.util.UUID
import kotlinx.datetime.Instant

enum class Sender {
    User, Assistant
}

enum class MessageStatus {
    Sending, Sent, Failed
}

enum class MessageType {
    Text
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: Sender,
    val text: String,
    val timestamp: Instant,
    val status: MessageStatus = MessageStatus.Sent,
    val type: MessageType = MessageType.Text
)
