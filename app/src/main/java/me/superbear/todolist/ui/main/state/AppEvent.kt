package me.superbear.todolist.ui.main.state

import me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent
import me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent
import me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent
import me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent
import me.superbear.todolist.ui.main.sections.tasks.TaskEvent

/**
 * Task detail events
 */
sealed class TaskDetailEvent {
    data class ShowDetail(val taskId: Long) : TaskDetailEvent()
    object HideDetail : TaskDetailEvent()
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
    data class SetUseMockAssistant(val useMock: Boolean) : AppEvent()
    data class SetExecuteAssistantActions(val execute: Boolean) : AppEvent()
}
