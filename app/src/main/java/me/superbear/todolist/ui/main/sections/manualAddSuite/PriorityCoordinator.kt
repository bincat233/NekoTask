package me.superbear.todolist.ui.main.sections.manualAddSuite

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.ui.main.sections.logFailure
import me.superbear.todolist.ui.main.sections.taskDetail.TaskDetailCoordinator

/**
 * Owns the priority picker's transient UI state - reused by both the manual-add flow and the
 * task-detail flow - plus persisting a priority change to whichever task is currently open in
 * the detail sheet.
 */
class PriorityCoordinator(
    private val todoRepository: TodoRepository,
    private val taskDetailCoordinator: TaskDetailCoordinator,
    private val viewModelScope: CoroutineScope
) {
    private val _state = MutableStateFlow(PriorityState())
    val state: StateFlow<PriorityState> = _state.asStateFlow()

    fun handleEvent(event: PriorityEvent) {
        if (event is PriorityEvent.SetPriority) {
            val detail = taskDetailCoordinator.state.value
            val taskId = detail.selectedTaskId
            if (detail.isVisible && taskId != null) {
                viewModelScope.launch {
                    todoRepository.updateTask(taskId, priority = event.priority)
                        .logFailure("PriorityCoordinator", "Failed to update task priority: $taskId")
                }
            }
        }
        _state.update { PriorityReducer.reduce(it, event) }
    }

    /** Used when resetting the manual-add form after submit. */
    fun reset() {
        _state.update { PriorityReducer.reduce(it, PriorityEvent.SetPriority(Priority.DEFAULT)) }
    }
}
