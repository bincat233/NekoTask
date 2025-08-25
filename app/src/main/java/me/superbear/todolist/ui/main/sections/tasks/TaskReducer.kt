package me.superbear.todolist.ui.main.sections.tasks

import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus

/**
 * Pure function reducer for task state updates
 */
object TaskReducer {
    
    fun reduce(state: TaskState, event: TaskEvent): TaskState {
        return when (event) {
            is TaskEvent.LoadTasks -> {
                state.copy(isLoading = true)
            }
            is TaskEvent.TasksLoaded -> {
                state.copy(items = event.tasks, isLoading = false)
            }
            is TaskEvent.Add -> {
                state.copy(items = listOf(event.task) + state.items)
            }
            is TaskEvent.Update -> {
                state.copy(
                    items = state.items.map { 
                        if (it.id == event.task.id) event.task else it 
                    }
                )
            }
            is TaskEvent.Delete -> {
                state.copy(items = state.items.filterNot { it.id == event.taskId })
            }
            is TaskEvent.Toggle -> {
                val newStatus = if (event.task.status == TaskStatus.OPEN) TaskStatus.DONE else TaskStatus.OPEN
                val updatedTask = event.task.copy(status = newStatus)
                state.copy(
                    items = state.items.map {
                        if (it.id == event.task.id) updatedTask else it
                    }
                )
            }
            is TaskEvent.AddSubtask -> {
                // This will be handled by the ViewModel to create the actual task
                // The reducer just returns the current state
                state
            }
            is TaskEvent.ToggleSubtask -> {
                val newStatus = if (event.done) TaskStatus.DONE else TaskStatus.OPEN
                state.copy(
                    items = state.items.map { task ->
                        if (task.id == event.childId) {
                            task.copy(status = newStatus)
                        } else {
                            task
                        }
                    }
                )
            }
        }
    }
}
