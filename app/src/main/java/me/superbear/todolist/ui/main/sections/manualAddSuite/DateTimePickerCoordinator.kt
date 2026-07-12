package me.superbear.todolist.ui.main.sections.manualAddSuite

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the date/time picker's transient UI state - reused by both the manual-add flow and the
 * task-detail flow - plus persisting a due-date change to whichever task is currently open in
 * the detail sheet.
 */
class DateTimePickerCoordinator(
    private val viewModelScope: CoroutineScope
) {
    private val _state = MutableStateFlow(DateTimePickerState())
    val state: StateFlow<DateTimePickerState> = _state.asStateFlow()

    fun handleEvent(event: DateTimePickerEvent) {
        _state.update { DateTimePickerReducer.reduce(it, event) }
    }

    /** Used when resetting the manual-add form after submit. */
    fun reset() {
        _state.update {
            DateTimePickerReducer.reduce(it, DateTimePickerEvent.Close).copy(selectedDueDateMs = null)
        }
    }
}
