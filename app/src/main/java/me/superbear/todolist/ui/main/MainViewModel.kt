package me.superbear.todolist.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ai.koog.prompt.llm.LLMProvider
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.assistant.ChatAgent
import me.superbear.todolist.assistant.LlmRuntime
import me.superbear.todolist.assistant.TodoAgent
import me.superbear.todolist.data.SeedManager
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.ui.main.sections.chatOverlay.ChatCoordinator
import me.superbear.todolist.ui.main.sections.longTermMemory.LongTermMemoryCoordinator
import me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerCoordinator
import me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddCoordinator
import me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityCoordinator
import me.superbear.todolist.ui.main.sections.subtaskDivision.SubtaskDivisionCoordinator
import me.superbear.todolist.ui.main.sections.taskDetail.TaskDetailCoordinator
import me.superbear.todolist.ui.main.sections.tasks.TaskListCoordinator
import me.superbear.todolist.ui.main.state.AppEvent
import me.superbear.todolist.ui.main.state.AppState
import me.superbear.todolist.data.repository.LongTermMemoryRepository
import me.superbear.todolist.ui.main.sections.appShell.AppShellCoordinator
import me.superbear.todolist.ui.settings.SettingsCoordinator
import me.superbear.todolist.ui.settings.SettingsState

/**
 * Refactored MainViewModel - Thin orchestrator that routes events to reducers
 * and handles side effects (I/O, async operations).
 *
 * Now uses Koog AIAgent via TodoAgent for AI chat and tools.
 */
class MainViewModel(
    application: Application,
    private val todoRepository: TodoRepository,
    private val longTermMemoryRepository: LongTermMemoryRepository,
    private val chatAgent: ChatAgent,
    private val llmRuntime: LlmRuntime
) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // Coordinators are declared in dependency order - each may only depend on ones declared above it.
    private val appShellCoordinator = AppShellCoordinator()
    private val longTermMemoryCoordinator = LongTermMemoryCoordinator(longTermMemoryRepository, viewModelScope)
    private val settingsCoordinator = SettingsCoordinator(prefs, llmRuntime)
    private val taskListCoordinator = TaskListCoordinator(todoRepository, viewModelScope)
    private val taskDetailCoordinator = TaskDetailCoordinator(todoRepository, taskListCoordinator, viewModelScope)
    private val dateTimePickerCoordinator = DateTimePickerCoordinator(todoRepository, taskDetailCoordinator, viewModelScope)
    private val priorityCoordinator = PriorityCoordinator(todoRepository, taskDetailCoordinator, viewModelScope)
    private val manualAddCoordinator = ManualAddCoordinator(todoRepository, dateTimePickerCoordinator, priorityCoordinator, viewModelScope)
    private val chatCoordinator = ChatCoordinator(
        chatAgent, longTermMemoryRepository, taskListCoordinator, getApplication(), viewModelScope
    )
    private val subtaskDivisionCoordinator = SubtaskDivisionCoordinator(
        llmRuntime, todoRepository, taskListCoordinator, taskDetailCoordinator, viewModelScope
    )

    // Thin delegating wrappers so MainScreen.kt's direct-call surface doesn't need to change.
    val selectedProvider: StateFlow<LLMProvider> get() = settingsCoordinator.selectedProvider
    val currentApiKey: StateFlow<String> get() = settingsCoordinator.currentApiKey
    val selectedModel: StateFlow<String> get() = settingsCoordinator.selectedModel
    val availableModels: StateFlow<List<String>> get() = settingsCoordinator.availableModels
    val isLoadingModels: StateFlow<Boolean> get() = settingsCoordinator.isLoadingModels
    val PROVIDER_INFO get() = settingsCoordinator.PROVIDER_INFO
    fun selectProvider(provider: LLMProvider) = settingsCoordinator.selectProvider(provider)
    fun setApiKey(provider: LLMProvider, key: String) = settingsCoordinator.setApiKey(provider, key)
    fun selectModel(model: String) = settingsCoordinator.selectModel(model)
    fun refreshModels() = settingsCoordinator.refreshModels()
    fun updateSettings(settings: SettingsState) = settingsCoordinator.updateSettings(settings)
    fun updateLanguage(languageTag: String) = settingsCoordinator.updateLanguage(languageTag)

    val settingsState: StateFlow<SettingsState> = combine(
        settingsCoordinator.settingsState,
        longTermMemoryCoordinator.memories
    ) { settings, memories ->
        settings.copy(longTermMemories = memories)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())

    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    init {
        viewModelScope.launch {
            appShellCoordinator.state.collect { appShellState ->
                _appState.update { it.copy(appShellState = appShellState) }
            }
        }
        viewModelScope.launch {
            taskListCoordinator.taskState.collect { taskState ->
                _appState.update { it.copy(taskState = taskState) }
            }
        }
        viewModelScope.launch {
            taskDetailCoordinator.state.collect { taskDetailState ->
                _appState.update { it.copy(taskDetailState = taskDetailState) }
            }
        }
        viewModelScope.launch {
            dateTimePickerCoordinator.state.collect { dateTimePickerState ->
                _appState.update { it.copy(dateTimePickerState = dateTimePickerState) }
            }
        }
        viewModelScope.launch {
            priorityCoordinator.state.collect { priorityState ->
                _appState.update { it.copy(priorityState = priorityState) }
            }
        }
        viewModelScope.launch {
            manualAddCoordinator.state.collect { manualAddState ->
                _appState.update { it.copy(manualAddState = manualAddState) }
            }
        }
        viewModelScope.launch {
            chatCoordinator.state.collect { chatOverlayState ->
                _appState.update { it.copy(chatOverlayState = chatOverlayState) }
            }
        }
    }

    fun resetSampleData() = taskListCoordinator.resetSampleData()

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.AppShell -> appShellCoordinator.handleEvent(event.event)
            is AppEvent.Task -> taskListCoordinator.handleEvent(event.event)
            is AppEvent.ChatOverlay -> chatCoordinator.handleEvent(event.event)
            is AppEvent.ManualAdd -> manualAddCoordinator.handleEvent(event.event)
            is AppEvent.DateTimePicker -> dateTimePickerCoordinator.handleEvent(event.event)
            is AppEvent.Priority -> priorityCoordinator.handleEvent(event.event)
            is AppEvent.TaskDetail -> taskDetailCoordinator.handleEvent(event.event)
            is AppEvent.SubtaskDivision -> subtaskDivisionCoordinator.handleEvent(event.event)
            is AppEvent.LongTermMemory -> longTermMemoryCoordinator.handleEvent(event.event)
        }
    }

    fun getChildren(parentId: Long): List<Task> = taskListCoordinator.getChildren(parentId)

    fun getParentProgress(parentId: Long): Pair<Int, Int> = taskListCoordinator.getParentProgress(parentId)

    fun getSelectedTask(): Task? = taskDetailCoordinator.getSelectedTask()

    companion object {
        /**
         * Production wiring for [MainViewModel]. Compose's default [androidx.lifecycle.viewmodel.compose.viewModel]
         * factory only knows how to satisfy a single-`Application` constructor via reflection, so once
         * MainViewModel takes real dependencies it needs an explicit factory instead - this also is the seam
         * that lets Compose UI tests build a MainViewModel with a fake [ChatAgent].
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val seedManager = if (BuildConfig.DEBUG) SeedManager(application) else null
                val todoRepository = TodoRepository(application, seedManager)
                val longTermMemoryRepository = LongTermMemoryRepository(todoRepository.database.longTermMemoryDao())
                val todoAgent = TodoAgent(todoRepository, longTermMemoryRepository)
                MainViewModel(
                    application = application,
                    todoRepository = todoRepository,
                    longTermMemoryRepository = longTermMemoryRepository,
                    chatAgent = todoAgent,
                    llmRuntime = todoAgent
                )
            }
        }
    }
}
