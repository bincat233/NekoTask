package me.superbear.todolist

import kotlinx.serialization.Serializable

sealed class AssistantAction {
    data class AddTask(
        val title: String,
        val notes: String? = null,
        val dueAtIso: String? = null,
        val priority: String? = null
    ) : AssistantAction()
}