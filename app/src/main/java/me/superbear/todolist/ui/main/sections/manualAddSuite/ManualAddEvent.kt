package me.superbear.todolist.ui.main.sections.manualAddSuite

/**
 * Manual add domain events
 */
sealed class ManualAddEvent {
    object Open : ManualAddEvent()
    object Close : ManualAddEvent()
    data class ChangeTitle(val value: String) : ManualAddEvent()
    data class ChangeDescription(val value: String) : ManualAddEvent()
    data class Submit(val title: String, val description: String?) : ManualAddEvent()
    object Reset : ManualAddEvent()
}
