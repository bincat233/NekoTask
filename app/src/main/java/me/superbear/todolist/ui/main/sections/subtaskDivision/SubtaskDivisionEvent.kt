package me.superbear.todolist.ui.main.sections.subtaskDivision

import me.superbear.todolist.assistant.subtask.DivisionStrategy

/**
 * 子任务划分事件
 */
sealed class SubtaskDivisionEvent {
    data class GenerateSuggestions(
        val taskId: Long,
        val strategy: DivisionStrategy? = null,
        val maxSubtasks: Int? = null,
        val context: String? = null,
        val useAI: Boolean = true
    ) : SubtaskDivisionEvent()

    data class CreateFromSuggestions(
        val taskId: Long,
        val strategy: DivisionStrategy? = null,
        val maxSubtasks: Int? = null,
        val context: String? = null,
        val useAI: Boolean = true
    ) : SubtaskDivisionEvent()

    data class BatchDivide(
        val taskIds: List<Long>,
        val strategy: DivisionStrategy? = null,
        val useAI: Boolean = true
    ) : SubtaskDivisionEvent()
}
