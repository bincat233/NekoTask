package me.superbear.todolist.ui.main.sections.tasks

import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus

/**
 * Task section state
 */
data class TaskState(
    val items: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTaskId: Long? = null,
    val showDetail: Boolean = false
) {
    val unfinishedItems: List<Task> get() = items.filter { it.status == TaskStatus.OPEN }
    val finishedItems: List<Task> get() = items.filter { it.status == TaskStatus.DONE }
    val openParents: List<Task> get() = unfinishedItems.filter { it.parentId == null }
    val doneParents: List<Task> get() = finishedItems.filter { it.parentId == null }
    val selectedTask: Task? get() = selectedTaskId?.let { id -> items.find { it.id == id } }
}
