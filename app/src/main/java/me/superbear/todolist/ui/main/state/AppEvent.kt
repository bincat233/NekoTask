package me.superbear.todolist.ui.main.state

import me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent
import me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent
import me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent
import me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent
import me.superbear.todolist.ui.main.sections.tasks.TaskEvent
import me.superbear.todolist.assistant.subtask.DivisionStrategy
import me.superbear.todolist.domain.entities.LongTermMemory

/**
 * Task detail events
 */
sealed class TaskDetailEvent {
    data class ShowDetail(val taskId: Long) : TaskDetailEvent()
    object HideDetail : TaskDetailEvent()
    data class EditTitle(val title: String) : TaskDetailEvent()
    data class EditContent(val content: String) : TaskDetailEvent()
    data class UpdatePriority(val taskId: Long, val priority: me.superbear.todolist.domain.entities.Priority) : TaskDetailEvent()
    data class DeleteTask(val taskId: Long) : TaskDetailEvent()
}

/**
 * 子任务划分事件
 */
sealed class SubtaskDivisionEvent {
    data class GenerateSuggestions(
        val taskId: Long,
        val strategy: DivisionStrategy? = null,
        val maxSubtasks: Int? = null,
        val context: String? = null,
        val useAI: Boolean = true
    ) : SubtaskDivisionEvent()
    
    data class CreateFromSuggestions(
        val taskId: Long,
        val strategy: DivisionStrategy? = null,
        val maxSubtasks: Int? = null,
        val context: String? = null,
        val useAI: Boolean = true
    ) : SubtaskDivisionEvent()
    
    data class BatchDivide(
        val taskIds: List<Long>,
        val strategy: DivisionStrategy? = null,
        val useAI: Boolean = true
    ) : SubtaskDivisionEvent()
}

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

/**
 * Root UI Event that wraps all sub-domain events
 */
sealed class AppEvent {
    data class Task(val event: TaskEvent) : AppEvent()
    data class ChatOverlay(val event: ChatOverlayEvent) : AppEvent()
    data class ManualAdd(val event: ManualAddEvent) : AppEvent()
    data class DateTimePicker(val event: DateTimePickerEvent) : AppEvent()
    data class Priority(val event: PriorityEvent) : AppEvent()
    data class TaskDetail(val event: TaskDetailEvent) : AppEvent()
    data class SubtaskDivision(val event: SubtaskDivisionEvent) : AppEvent()
    data class LongTermMemory(val event: LongTermMemoryEvent) : AppEvent()
    data class SetUseMockAssistant(val useMock: Boolean) : AppEvent()
    data class SetExecuteAssistantActions(val execute: Boolean) : AppEvent()
}
