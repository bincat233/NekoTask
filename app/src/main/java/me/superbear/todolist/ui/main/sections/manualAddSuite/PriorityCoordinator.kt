package me.superbear.todolist.ui.main.sections.manualAddSuite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.superbear.todolist.domain.entities.Priority

/**
 * Owns the priority picker's transient UI state - reused by both the manual-add flow and the
 * task-detail flow - plus persisting a priority change to whichever task is currently open in
 * the detail sheet.
 */
class PriorityCoordinator {
    private val _state = MutableStateFlow(PriorityState())
    val state: StateFlow<PriorityState> = _state.asStateFlow()

    fun handleEvent(event: PriorityEvent) {
        _state.update { PriorityReducer.reduce(it, event) }
    }

    /** Used when resetting the manual-add form after submit. */
    fun reset() {
        _state.update { PriorityReducer.reduce(it, PriorityEvent.SetPriority(Priority.DEFAULT)) }
    }
}
