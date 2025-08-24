package me.superbear.todolist.ui.main.sections.manualAddSuite

import me.superbear.todolist.domain.entities.Priority

/**
 * Priority domain events
 */
sealed class PriorityEvent {
    object OpenMenu : PriorityEvent()
    object CloseMenu : PriorityEvent()
    data class SetPriority(val priority: Priority) : PriorityEvent()
}
