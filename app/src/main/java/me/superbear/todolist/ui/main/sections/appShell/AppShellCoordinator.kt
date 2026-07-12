package me.superbear.todolist.ui.main.sections.appShell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppShellCoordinator {
    private val _state = MutableStateFlow(AppShellState())
    val state: StateFlow<AppShellState> = _state.asStateFlow()

    fun handleEvent(event: AppShellEvent) {
        when (event) {
            is AppShellEvent.NavigateTo -> {
                _state.update { it.copy(currentPage = event.page) }
            }
        }
    }
}
