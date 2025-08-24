package me.superbear.todolist.ui.main.sections.manualAddSuite

/**
 * Pure function reducer for priority state updates
 */
object PriorityReducer {
    
    fun reduce(state: PriorityState, event: PriorityEvent): PriorityState {
        return when (event) {
            is PriorityEvent.OpenMenu -> {
                state.copy(isMenuVisible = true)
            }
            is PriorityEvent.CloseMenu -> {
                state.copy(isMenuVisible = false)
            }
            is PriorityEvent.SetPriority -> {
                state.copy(selectedPriority = event.priority, isMenuVisible = false)
            }
        }
    }
}
