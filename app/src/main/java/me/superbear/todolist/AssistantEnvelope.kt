package me.superbear.todolist

data class AssistantEnvelope(
    val say: String? = null,
    val actions: List<AssistantAction> = emptyList()
)
