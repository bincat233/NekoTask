package me.superbear.todolist.ui.main.sections.taskDetail

import me.superbear.todolist.domain.entities.Priority

/**
 * Task detail events
 */
sealed class TaskDetailEvent {
    data class ShowDetail(val taskId: Long) : TaskDetailEvent()
    object HideDetail : TaskDetailEvent()
    data class EditTitle(val title: String) : TaskDetailEvent()
    data class EditContent(val content: String) : TaskDetailEvent()
    data class UpdatePriority(val taskId: Long, val priority: Priority) : TaskDetailEvent()
    data class UpdateDueDate(val taskId: Long, val timestamp: Long?) : TaskDetailEvent()
    data class DeleteTask(val taskId: Long) : TaskDetailEvent()
}
