package me.superbear.todolist.assistant

data class AssistantEnvelope(
    val say: String? = null,
    val actions: List<AssistantAction> = emptyList()
)
