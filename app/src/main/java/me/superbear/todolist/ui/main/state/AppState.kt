package me.superbear.todolist.ui.main.state

import me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayState
import me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerState
import me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddState
import me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityState
import me.superbear.todolist.ui.main.sections.tasks.TaskState

/**
 * Root UI State that composes all sub-domain states
 */
data class AppState(
    val taskState: TaskState = TaskState(),
    val chatOverlayState: ChatOverlayState = ChatOverlayState(),
    val manualAddState: ManualAddState = ManualAddState(),
    val dateTimePickerState: DateTimePickerState = DateTimePickerState(),
    val priorityState: PriorityState = PriorityState(),
    val useMockAssistant: Boolean = true,
    val executeAssistantActions: Boolean = true
)
