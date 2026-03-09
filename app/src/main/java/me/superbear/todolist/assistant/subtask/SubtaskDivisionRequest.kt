package me.superbear.todolist.assistant.subtask

import kotlinx.serialization.Serializable
import me.superbear.todolist.domain.entities.Priority

/**
 * Subtask Division Request Data Model
 */
@Serializable
data class SubtaskDivisionRequest(
    val taskTitle: String,
    val taskContent: String? = null,
    val taskPriority: Priority = Priority.DEFAULT,
    val maxSubtasks: Int = 5,
    val strategy: DivisionStrategy = DivisionStrategy.BALANCED,
    val context: String? = null // Additional context information
)

/**
 * Subtask Division Strategy
 */
enum class DivisionStrategy {
    DETAILED,    // Detailed division, generate more fine-grained subtasks
    BALANCED,    // Balanced division, moderate number and granularity of subtasks
    SIMPLIFIED   // Simplified division, generate fewer high-level subtasks
}
