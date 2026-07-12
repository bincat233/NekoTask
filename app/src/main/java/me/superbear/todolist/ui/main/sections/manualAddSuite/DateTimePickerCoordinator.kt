package me.superbear.todolist.ui.main.sections.manualAddSuite

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.ui.main.sections.logFailure
import me.superbear.todolist.ui.main.sections.taskDetail.TaskDetailCoordinator

/**
 * Owns the date/time picker's transient UI state - reused by both the manual-add flow and the
 * task-detail flow - plus persisting a due-date change to whichever task is currently open in
 * the detail sheet.
 */
class DateTimePickerCoordinator(
    private val todoRepository: TodoRepository,
    private val taskDetailCoordinator: TaskDetailCoordinator,
    private val viewModelScope: CoroutineScope
) {
    private val _state = MutableStateFlow(DateTimePickerState())
    val state: StateFlow<DateTimePickerState> = _state.asStateFlow()

    fun handleEvent(event: DateTimePickerEvent) {
        if (event is DateTimePickerEvent.SetDueDate) {
            val detail = taskDetailCoordinator.state.value
            val taskId = detail.selectedTaskId
            if (detail.isVisible && taskId != null && event.timestamp != null) {
                viewModelScope.launch {
                    val dueAt = Instant.fromEpochMilliseconds(event.timestamp)
                    val updatedAt = Clock.System.now().toEpochMilliseconds()
                    todoRepository.updateDueAt(taskId, dueAt, updatedAt)
                        .logFailure("DateTimePickerCoordinator", "Failed to update task due date: $taskId")
                }
            }
        }
        _state.update { DateTimePickerReducer.reduce(it, event) }
    }

    /** Used when resetting the manual-add form after submit. */
    fun reset() {
        _state.update {
            DateTimePickerReducer.reduce(it, DateTimePickerEvent.Close).copy(selectedDueDateMs = null)
        }
    }
}
