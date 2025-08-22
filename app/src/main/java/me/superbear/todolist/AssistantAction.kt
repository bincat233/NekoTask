package me.superbear.todolist

sealed class AssistantAction {
    data class AddTask(
        val title: String,
        val notes: String? = null,
        val dueAtIso: String? = null,
        val priority: String? = null
    ) : AssistantAction()
}