package me.superbear.todolist.ui.main.sections.longTermMemory

import me.superbear.todolist.domain.entities.LongTermMemory

/**
 * 长期记忆事件
 */
sealed class LongTermMemoryEvent {
    data class AddMemory(
        val content: String,
        val category: String,
        val importance: Int,
        val isActive: Boolean
    ) : LongTermMemoryEvent()

    data class EditMemory(
        val memory: LongTermMemory,
        val content: String,
        val category: String,
        val importance: Int,
        val isActive: Boolean
    ) : LongTermMemoryEvent()

    data class DeleteMemory(val memory: LongTermMemory) : LongTermMemoryEvent()

    data class ToggleMemoryActive(
        val memory: LongTermMemory,
        val isActive: Boolean
    ) : LongTermMemoryEvent()

    object LoadMemories : LongTermMemoryEvent()
}
