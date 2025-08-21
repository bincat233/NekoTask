package me.superbear.todolist

data class Task(
    val id: Long,
    val title: String,
    val createdAtIso: String,
    val notes: String? = null,
    val dueAtIso: String? = null,
    val priority: String? = null,
    val status: String = "OPEN",
    val aiInsights: String? = null,
    val origin: String? = null,
    val updatedAtIso: String? = null,
    val children: List<Task>? = null
)
