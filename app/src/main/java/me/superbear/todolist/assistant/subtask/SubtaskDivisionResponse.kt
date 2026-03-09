package me.superbear.todolist.assistant.subtask

import kotlinx.serialization.Serializable
import me.superbear.todolist.domain.entities.Priority

/**
 * Subtask Division Response Data Model
 */
@Serializable
data class SubtaskDivisionResponse(
    val originalTask: String,
    val subtasks: List<SubtaskSuggestion>,
    val reasoning: String? = null // AI's decomposition reasoning explanation
)

/**
 * Subtask Suggestion
 */
@Serializable
data class SubtaskSuggestion(
    val title: String,
    val content: String? = null,
    val priority: Priority = Priority.DEFAULT,
    val estimatedOrder: Int, // Suggested execution order
    val dependencies: List<Int> = emptyList() // Dependencies on other subtasks by index
)
