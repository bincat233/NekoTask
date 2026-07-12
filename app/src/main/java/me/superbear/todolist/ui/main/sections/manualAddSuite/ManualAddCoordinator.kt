package me.superbear.todolist.ui.main.sections.manualAddSuite

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.ui.main.sections.logFailure

/**
 * Owns the manual-add form's state and submit orchestration, resetting the shared
 * date/time and priority pickers on submit.
 */
class ManualAddCoordinator(
    private val todoRepository: TodoRepository,
    private val dateTimePickerCoordinator: DateTimePickerCoordinator,
    private val priorityCoordinator: PriorityCoordinator,
    private val viewModelScope: CoroutineScope
) {
    private val _state = MutableStateFlow(ManualAddState())
    val state: StateFlow<ManualAddState> = _state.asStateFlow()

    fun handleEvent(event: ManualAddEvent) {
        when (event) {
            is ManualAddEvent.Submit -> handleSubmit(event.title, event.description)
            else -> _state.update { ManualAddReducer.reduce(it, event) }
        }
    }

    private fun handleSubmit(title: String, description: String?) {
        val dueAtInstant = dateTimePickerCoordinator.state.value.selectedDueDateMs?.let {
            Instant.fromEpochMilliseconds(it)
        }
        val priority = priorityCoordinator.state.value.selectedPriority

        viewModelScope.launch {
            todoRepository.addTask(
                title = title, parentId = null, content = description,
                priority = priority, dueAt = dueAtInstant
            ).logFailure("ManualAddCoordinator", "Failed to add task: $title")
        }
        resetForm()
    }

    private fun resetForm() {
        _state.update { ManualAddReducer.reduce(it, ManualAddEvent.Reset) }
        dateTimePickerCoordinator.reset()
        priorityCoordinator.reset()
    }
}
