package me.superbear.todolist.ui.main.sections.appShell

sealed class AppPage {
    object TaskList : AppPage()
    object Settings : AppPage()
}

sealed class AppMode {
    object Normal : AppMode()
    object ManualAdd : AppMode()
    object ChatFullscreen : AppMode()
}

sealed class AppShellBackAction {
    object HideTaskDetail : AppShellBackAction()
    object CloseManualAdd : AppShellBackAction()
    object ExitFullscreenChat : AppShellBackAction()
    object NavigateToTaskList : AppShellBackAction()
}

data class AppShellState(
    val currentPage: AppPage = AppPage.TaskList
)

fun resolveAppMode(
    manualAddOpen: Boolean,
    chatOverlayMode: String
): AppMode {
    return when {
        manualAddOpen -> AppMode.ManualAdd
        chatOverlayMode == "fullscreen" -> AppMode.ChatFullscreen
        else -> AppMode.Normal
    }
}

fun resolveBackAction(
    currentPage: AppPage,
    currentMode: AppMode,
    taskDetailVisible: Boolean
): AppShellBackAction? {
    return when {
        taskDetailVisible -> AppShellBackAction.HideTaskDetail
        currentMode == AppMode.ManualAdd -> AppShellBackAction.CloseManualAdd
        currentMode == AppMode.ChatFullscreen -> AppShellBackAction.ExitFullscreenChat
        currentPage == AppPage.Settings -> AppShellBackAction.NavigateToTaskList
        else -> null
    }
}
