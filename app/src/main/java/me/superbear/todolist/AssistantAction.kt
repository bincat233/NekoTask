package me.superbear.todolist

sealed class AssistantAction {
    data class AddTask(
        val title: String,
        val notes: String? = null,
        val dueAtIso: String? = null,
        val priority: String? = null
    ) : AssistantAction()

    data class DeleteTask(
        val id: Long
    ) : AssistantAction()

    data class UpdateTask(
        val id: Long,
        val title: String? = null,
        val notes: String? = null,
        val dueAtIso: String? = null,
        val priority: String? = null
    ) : AssistantAction()
}