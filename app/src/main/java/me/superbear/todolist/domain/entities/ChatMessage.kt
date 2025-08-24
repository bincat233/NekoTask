package me.superbear.todolist.domain.entities

import java.util.UUID
import kotlinx.datetime.Instant

enum class Sender {
    User, Assistant
}

enum class MessageStatus {
    Sending, Sent, Failed
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: Sender,
    val text: String,
    val timestamp: Instant,
    val status: MessageStatus = MessageStatus.Sent,
    val replyToId: String? = null, // 关联字段：助理消息回复的用户消息 ID
)
