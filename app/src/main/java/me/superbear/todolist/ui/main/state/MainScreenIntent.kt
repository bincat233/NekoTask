package me.superbear.todolist.ui.main.state

import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.ui.main.sections.appShell.AppPage
import me.superbear.todolist.ui.settings.SettingsState

/**
 * High-level user intents emitted by the MainScreen.
 * Decoupled from internal sub-domain routing details.
 */
sealed class MainScreenIntent {
    // Navigation / Page shell
    data class NavigateTo(val page: AppPage) : MainScreenIntent()
    object HandleBackPressed : MainScreenIntent()

    // Task actions
    data class ToggleTask(val task: Task) : MainScreenIntent()
    data class AddTaskDirectly(val title: String) : MainScreenIntent()
    data class ShowTaskDetail(val taskId: Long) : MainScreenIntent()
    object HideTaskDetail : MainScreenIntent()

    // Task editing
    data class EditTaskTitle(val title: String) : MainScreenIntent()
    data class EditTaskContent(val content: String) : MainScreenIntent()
    data class UpdateTaskPriority(val taskId: Long, val priority: Priority) : MainScreenIntent()
    data class DeleteTask(val taskId: Long) : MainScreenIntent()

    // Subtask actions
    data class AddSubtask(val parentId: Long, val title: String, val order: Long? = null) : MainScreenIntent()
    data class ToggleSubtask(val subtaskId: Long, val done: Boolean) : MainScreenIntent()
    data class EditSubtaskTitle(val subtaskId: Long, val title: String) : MainScreenIntent()
    data class DeleteSubtask(val subtaskId: Long) : MainScreenIntent()
    data class DivideSubtasks(val taskId: Long) : MainScreenIntent()

    // DateTime Picker
    data class OpenDateTimePicker(val initialDueDateMs: Long?) : MainScreenIntent()
    data class SetDueDate(val timestamp: Long?) : MainScreenIntent()
    object CloseDateTimePicker : MainScreenIntent()

    // Priority Picker (Manual Add)
    object OpenPriorityMenu : MainScreenIntent()
    data class SetManualAddPriority(val priority: Priority) : MainScreenIntent()
    object ClosePriorityMenu : MainScreenIntent()

    // Manual Add Form
    data class TypeManualAddTitle(val title: String) : MainScreenIntent()
    data class TypeManualAddDescription(val description: String) : MainScreenIntent()
    object OpenManualAdd : MainScreenIntent()
    object CloseManualAdd : MainScreenIntent()
    object SubmitManualAdd : MainScreenIntent()

    // Chat Overlay
    data class SendChatMessage(val text: String) : MainScreenIntent()
    data class SetChatOverlayMode(val mode: String) : MainScreenIntent()
    data class FabMeasured(val widthDp: androidx.compose.ui.unit.Dp) : MainScreenIntent()
    data class DismissPeekMessage(val id: String) : MainScreenIntent()

    // Long Term Memory
    data class AddMemory(val content: String, val category: String, val importance: Int, val isActive: Boolean) : MainScreenIntent()
    data class EditMemory(val memory: me.superbear.todolist.domain.entities.LongTermMemory, val content: String, val category: String, val importance: Int, val isActive: Boolean) : MainScreenIntent()
    data class DeleteMemory(val memory: me.superbear.todolist.domain.entities.LongTermMemory) : MainScreenIntent()
    data class ToggleMemoryActive(val memory: me.superbear.todolist.domain.entities.LongTermMemory, val isActive: Boolean) : MainScreenIntent()

    // Settings
    data class SaveSettings(val state: SettingsState) : MainScreenIntent()
}
