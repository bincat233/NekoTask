package me.superbear.todolist.ui.main.sections.manualAddSuite

/**
 * Pure function reducer for date time picker state updates
 */
object DateTimePickerReducer {
    
    fun reduce(state: DateTimePickerState, event: DateTimePickerEvent): DateTimePickerState {
        return when (event) {
            is DateTimePickerEvent.Open -> {
                state.copy(isVisible = true)
            }
            is DateTimePickerEvent.Close -> {
                state.copy(isVisible = false)
            }
            is DateTimePickerEvent.SetDueDate -> {
                state.copy(selectedDueDateMs = event.timestamp, isVisible = false)
            }
        }
    }
}
