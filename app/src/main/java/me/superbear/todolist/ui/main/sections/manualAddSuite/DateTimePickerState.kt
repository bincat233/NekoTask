package me.superbear.todolist.ui.main.sections.manualAddSuite

/**
 * Date time picker domain state
 */
data class DateTimePickerState(
    val isVisible: Boolean = false,
    val selectedDueDateMs: Long? = null
)
