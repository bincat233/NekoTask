package me.superbear.todolist.ui.main.sections.manualAddSuite

/**
 * Manual add section state - manages manual task creation form
 */
data class ManualAddState(
    val isOpen: Boolean = false,
    val title: String = "",
    val description: String = ""
)
