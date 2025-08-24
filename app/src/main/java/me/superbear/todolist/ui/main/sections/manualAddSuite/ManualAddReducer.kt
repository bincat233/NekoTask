package me.superbear.todolist.ui.main.sections.manualAddSuite

/**
 * Pure function reducer for manual add state updates
 */
object ManualAddReducer {
    
    fun reduce(state: ManualAddState, event: ManualAddEvent): ManualAddState {
        return when (event) {
            is ManualAddEvent.Open -> {
                state.copy(isOpen = true)
            }
            is ManualAddEvent.Close -> {
                state.copy(isOpen = false)
            }
            is ManualAddEvent.ChangeTitle -> {
                state.copy(title = event.value)
            }
            is ManualAddEvent.ChangeDescription -> {
                state.copy(description = event.value)
            }
            is ManualAddEvent.Submit -> {
                // The actual task creation is handled by ViewModel
                // Reducer just returns current state
                state
            }
            is ManualAddEvent.Reset -> {
                ManualAddState() // Reset to default state
            }
        }
    }
}
