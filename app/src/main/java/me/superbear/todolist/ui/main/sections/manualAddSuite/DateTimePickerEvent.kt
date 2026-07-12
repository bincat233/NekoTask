package me.superbear.todolist.ui.main.sections.manualAddSuite

/**
 * Date time picker domain events
 */
sealed class DateTimePickerEvent {
    data class Open(val initialDueDateMs: Long? = null) : DateTimePickerEvent()
    object Close : DateTimePickerEvent()
    data class SetDueDate(val timestamp: Long?) : DateTimePickerEvent()
}
