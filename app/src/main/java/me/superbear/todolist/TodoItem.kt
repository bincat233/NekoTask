package me.superbear.todolist

import kotlinx.datetime.Instant

enum class Priority {
    LOW, MEDIUM, HIGH, DEFAULT
}

data class Task(
    val id: Long,
    val title: String,
    val createdAt: Instant,
    val notes: String? = null,
    val dueAt: Instant? = null,
    val priority: Priority = Priority.DEFAULT,
    val status: String = "OPEN",
    val aiInsights: String? = null,
    val origin: String? = null,
    val updatedAt: Instant? = null,
    val children: List<Task>? = null
)
