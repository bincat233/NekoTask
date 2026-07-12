package me.superbear.todolist.ui.main.sections.appShell

sealed class AppShellEvent {
    data class NavigateTo(val page: AppPage) : AppShellEvent()
}
