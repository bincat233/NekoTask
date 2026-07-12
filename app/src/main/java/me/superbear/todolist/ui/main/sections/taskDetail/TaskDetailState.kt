package me.superbear.todolist.ui.main.sections.taskDetail

/**
 * Task detail sheet state
 */
data class TaskDetailState(
    val isVisible: Boolean = false,
    val selectedTaskId: Long? = null,
    val editedTitle: String = "",
    val editedContent: String = "",
    val isSubtaskDivisionLoading: Boolean = false
)
