package me.superbear.todolist.ui.main.sections.manualAddSuite

import me.superbear.todolist.domain.entities.Priority

/**
 * Priority menu domain state
 */
data class PriorityState(
    val isMenuVisible: Boolean = false,
    val selectedPriority: Priority = Priority.DEFAULT
)
