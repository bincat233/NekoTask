package me.superbear.todolist

data class TodoItem(
    val id: String,
    val text: String,
    var isCompleted: Boolean,
    val notes: String? = null
    // val subItems: List<TodoItem>? = null // Can be added later
)
