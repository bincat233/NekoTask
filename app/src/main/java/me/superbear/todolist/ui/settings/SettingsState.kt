package me.superbear.todolist.ui.settings

import me.superbear.todolist.assistant.subtask.DivisionStrategy
import me.superbear.todolist.domain.entities.LongTermMemory

/**
 * 设置页面的状态数据类
 */
data class SettingsState(
    val aiDivisionStrategy: DivisionStrategy = DivisionStrategy.BALANCED,
    val useAI: Boolean = true,
    val maxSubtasks: Int = 5,
    val longTermMemories: List<LongTermMemory> = emptyList(),
    val isMemoryDialogVisible: Boolean = false,
    val editingMemory: LongTermMemory? = null
)
