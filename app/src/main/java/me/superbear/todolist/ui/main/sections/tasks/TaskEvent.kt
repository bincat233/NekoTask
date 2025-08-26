package me.superbear.todolist.ui.main.sections.tasks

import me.superbear.todolist.domain.entities.Task

/**
 * Task domain events
 */
sealed class TaskEvent {
    data class Toggle(val task: Task) : TaskEvent()
    data class Add(val task: Task) : TaskEvent()
    data class Update(val task: Task) : TaskEvent()
    data class Delete(val taskId: Long) : TaskEvent()
    data class AddSubtask(val parentId: Long, val title: String) : TaskEvent()
    data class ToggleSubtask(val childId: Long, val done: Boolean) : TaskEvent()
    object LoadTasks : TaskEvent()
    data class TasksLoaded(val tasks: List<Task>) : TaskEvent()
}
