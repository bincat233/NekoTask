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
import me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent
import me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddCoordinator
import me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityCoordinator
import me.superbear.todolist.ui.main.sections.subtaskDivision.SubtaskDivisionCoordinator
import me.superbear.todolist.ui.main.sections.taskDetail.TaskDetailCoordinator
import me.superbear.todolist.ui.main.sections.taskDetail.TaskDetailEvent
import me.superbear.todolist.ui.main.sections.tasks.TaskListCoordinator
import me.superbear.todolist.ui.main.state.MainScreenIntent
import me.superbear.todolist.ui.main.state.AppState
import me.superbear.todolist.data.model.AppDatabase
import androidx.room.Room
import me.superbear.todolist.data.repository.LongTermMemoryRepository
import me.superbear.todolist.ui.main.sections.appShell.AppPage
import me.superbear.todolist.ui.main.sections.appShell.AppShellCoordinator
import me.superbear.todolist.ui.main.sections.appShell.AppShellEvent
import me.superbear.todolist.ui.main.sections.appShell.AppShellBackAction
import me.superbear.todolist.ui.main.sections.appShell.resolveAppMode
import me.superbear.todolist.ui.main.sections.appShell.resolveBackAction
import me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent
import me.superbear.todolist.ui.main.sections.longTermMemory.LongTermMemoryEvent
import me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent
import me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent
import me.superbear.todolist.ui.main.sections.subtaskDivision.SubtaskDivisionEvent
import me.superbear.todolist.ui.main.sections.tasks.TaskEvent
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
    private val dateTimePickerCoordinator = DateTimePickerCoordinator(viewModelScope)
    private val priorityCoordinator = PriorityCoordinator()
    private val manualAddCoordinator = ManualAddCoordinator(todoRepository, dateTimePickerCoordinator, priorityCoordinator, viewModelScope)
    private val chatCoordinator = ChatCoordinator(
        chatAgent, longTermMemoryRepository, taskListCoordinator, getApplication(), viewModelScope
    )
    private val subtaskDivisionCoordinator = SubtaskDivisionCoordinator(
        llmRuntime, todoRepository, taskListCoordinator, viewModelScope
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
            subtaskDivisionCoordinator.state.collect { subtaskDivisionState ->
                _appState.update { it.copy(subtaskDivisionState = subtaskDivisionState) }
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

    fun onEvent(event: MainScreenIntent) {
        when (event) {
            is MainScreenIntent.NavigateTo -> appShellCoordinator.handleEvent(AppShellEvent.NavigateTo(event.page))
            is MainScreenIntent.HandleBackPressed -> handleBackPressed()

            // Task List
            is MainScreenIntent.ToggleTask -> taskListCoordinator.handleEvent(TaskEvent.Toggle(event.task))
            is MainScreenIntent.AddTaskDirectly -> taskListCoordinator.handleEvent(TaskEvent.AddSubtask(0L, event.title, null))

            // Task Detail
            is MainScreenIntent.ShowTaskDetail -> taskDetailCoordinator.handleEvent(TaskDetailEvent.ShowDetail(event.taskId))
            is MainScreenIntent.HideTaskDetail -> taskDetailCoordinator.handleEvent(TaskDetailEvent.HideDetail)
            is MainScreenIntent.EditTaskTitle -> taskDetailCoordinator.handleEvent(TaskDetailEvent.EditTitle(event.title))
            is MainScreenIntent.EditTaskContent -> taskDetailCoordinator.handleEvent(TaskDetailEvent.EditContent(event.content))
            is MainScreenIntent.UpdateTaskPriority -> taskDetailCoordinator.handleEvent(TaskDetailEvent.UpdatePriority(event.taskId, event.priority))
            is MainScreenIntent.DeleteTask -> taskDetailCoordinator.handleEvent(TaskDetailEvent.DeleteTask(event.taskId))

            // Subtasks
            is MainScreenIntent.AddSubtask -> taskListCoordinator.handleEvent(TaskEvent.AddSubtask(event.parentId, event.title, event.order))
            is MainScreenIntent.ToggleSubtask -> taskListCoordinator.handleEvent(TaskEvent.ToggleSubtask(event.subtaskId, event.done))
            is MainScreenIntent.EditSubtaskTitle -> taskListCoordinator.handleEvent(TaskEvent.UpdateSubtaskTitle(event.subtaskId, event.title))
            is MainScreenIntent.DeleteSubtask -> taskListCoordinator.handleEvent(TaskEvent.DeleteSubtask(event.subtaskId))
            is MainScreenIntent.DivideSubtasks -> {
                val settings = settingsCoordinator.settingsState.value
                subtaskDivisionCoordinator.handleEvent(
                    SubtaskDivisionEvent.CreateFromSuggestions(
                        taskId = event.taskId,
                        strategy = settings.aiDivisionStrategy,
                        maxSubtasks = settings.maxSubtasks,
                        useAI = settings.useAI
                    )
                )
            }

            // DateTime Picker
            is MainScreenIntent.OpenDateTimePicker -> dateTimePickerCoordinator.handleEvent(DateTimePickerEvent.Open(event.initialDueDateMs))
            is MainScreenIntent.SetDueDate -> {
                val detail = taskDetailCoordinator.state.value
                val taskId = detail.selectedTaskId
                if (detail.isVisible && taskId != null) {
                    taskDetailCoordinator.handleEvent(TaskDetailEvent.UpdateDueDate(taskId, event.timestamp))
                } else {
                    dateTimePickerCoordinator.handleEvent(DateTimePickerEvent.SetDueDate(event.timestamp))
                }
            }
            is MainScreenIntent.CloseDateTimePicker -> dateTimePickerCoordinator.handleEvent(DateTimePickerEvent.Close)

            // Priority Picker
            is MainScreenIntent.OpenPriorityMenu -> priorityCoordinator.handleEvent(PriorityEvent.OpenMenu)
            is MainScreenIntent.SetManualAddPriority -> priorityCoordinator.handleEvent(PriorityEvent.SetPriority(event.priority))
            is MainScreenIntent.ClosePriorityMenu -> priorityCoordinator.handleEvent(PriorityEvent.CloseMenu)

            // Manual Add
            is MainScreenIntent.TypeManualAddTitle -> manualAddCoordinator.handleEvent(ManualAddEvent.ChangeTitle(event.title))
            is MainScreenIntent.TypeManualAddDescription -> manualAddCoordinator.handleEvent(ManualAddEvent.ChangeDescription(event.description))
            is MainScreenIntent.OpenManualAdd -> manualAddCoordinator.handleEvent(ManualAddEvent.Open)
            is MainScreenIntent.CloseManualAdd -> {
                manualAddCoordinator.handleEvent(ManualAddEvent.Close)
                dateTimePickerCoordinator.reset()
                priorityCoordinator.reset()
            }
            is MainScreenIntent.SubmitManualAdd -> {
                val manualState = appState.value.manualAddState
                manualAddCoordinator.handleEvent(ManualAddEvent.Submit(manualState.title, manualState.description))
            }

            // Chat
            is MainScreenIntent.SendChatMessage -> chatCoordinator.handleEvent(ChatOverlayEvent.SendMessage(event.text))
            is MainScreenIntent.SetChatOverlayMode -> chatCoordinator.handleEvent(ChatOverlayEvent.SetChatOverlayMode(event.mode))
            is MainScreenIntent.FabMeasured -> chatCoordinator.handleEvent(ChatOverlayEvent.FabMeasured(event.widthDp))
            is MainScreenIntent.DismissPeekMessage -> chatCoordinator.handleEvent(ChatOverlayEvent.DismissPeekMessage(event.id))

            // Long Term Memory
            is MainScreenIntent.AddMemory -> longTermMemoryCoordinator.handleEvent(LongTermMemoryEvent.AddMemory(event.content, event.category, event.importance, event.isActive))
            is MainScreenIntent.EditMemory -> longTermMemoryCoordinator.handleEvent(LongTermMemoryEvent.EditMemory(event.memory, event.content, event.category, event.importance, event.isActive))
            is MainScreenIntent.DeleteMemory -> longTermMemoryCoordinator.handleEvent(LongTermMemoryEvent.DeleteMemory(event.memory))
            is MainScreenIntent.ToggleMemoryActive -> longTermMemoryCoordinator.handleEvent(LongTermMemoryEvent.ToggleMemoryActive(event.memory, event.isActive))

            // Settings
            is MainScreenIntent.SaveSettings -> settingsCoordinator.updateSettings(event.state)
        }
    }

    private fun handleBackPressed() {
        val state = appState.value
        val currentPage = state.appShellState.currentPage
        val currentMode = resolveAppMode(state.manualAddState.isOpen, state.chatOverlayState.chatOverlayMode)
        val backAction = resolveBackAction(currentPage, currentMode, state.taskDetailState.isVisible)

        when (backAction) {
            AppShellBackAction.HideTaskDetail -> taskDetailCoordinator.handleEvent(TaskDetailEvent.HideDetail)
            AppShellBackAction.CloseManualAdd -> {
                manualAddCoordinator.handleEvent(ManualAddEvent.Close)
                dateTimePickerCoordinator.reset()
                priorityCoordinator.reset()
            }
            AppShellBackAction.ExitFullscreenChat -> chatCoordinator.handleEvent(
                ChatOverlayEvent.SetChatOverlayMode("peek")
            )
            AppShellBackAction.NavigateToTaskList -> appShellCoordinator.handleEvent(
                AppShellEvent.NavigateTo(AppPage.TaskList)
            )
            null -> Unit
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
                
                val database = Room.databaseBuilder(
                    application,
                    AppDatabase::class.java,
                    "todolist_database"
                ).apply {
                    addMigrations(AppDatabase.MIGRATION_4_5)
                    if (BuildConfig.DEBUG) {
                        fallbackToDestructiveMigration(false)
                    }
                }.build()

                val todoRepository = TodoRepository(database, seedManager)
                val longTermMemoryRepository = LongTermMemoryRepository(database.longTermMemoryDao())
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
