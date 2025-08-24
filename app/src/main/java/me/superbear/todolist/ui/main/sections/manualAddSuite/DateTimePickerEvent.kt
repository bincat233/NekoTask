package me.superbear.todolist.ui.main.sections.manualAddSuite

/**
 * Date time picker domain events
 */
sealed class DateTimePickerEvent {
    object Open : DateTimePickerEvent()
    object Close : DateTimePickerEvent()
    data class SetDueDate(val timestamp: Long?) : DateTimePickerEvent()
}
