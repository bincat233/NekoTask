package me.superbear.todolist.ui.main

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import me.superbear.todolist.R
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.assistant.ChatAgent
import me.superbear.todolist.assistant.TodoAgent
import me.superbear.todolist.data.SeedManager
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.domain.entities.ChatMessage
import me.superbear.todolist.domain.entities.MessageStatus
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.domain.entities.Sender
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus
import me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayReducer
import me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerReducer
import me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddReducer
import me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityReducer
import me.superbear.todolist.ui.main.state.TaskDetailEvent
import me.superbear.todolist.ui.main.state.SubtaskDivisionEvent
import me.superbear.todolist.ui.main.state.LongTermMemoryEvent
import me.superbear.todolist.ui.main.state.AppEvent
import me.superbear.todolist.ui.main.state.AppState
import me.superbear.todolist.assistant.subtask.DivisionStrategy
import me.superbear.todolist.assistant.subtask.SubtaskDivisionService
import me.superbear.todolist.assistant.subtask.SubtaskDivisionResponse
import me.superbear.todolist.assistant.subtask.SubtaskDivisionResult
import me.superbear.todolist.data.repository.LongTermMemoryRepository
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
    private val todoAgent: ChatAgent
) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _selectedProvider = MutableStateFlow<LLMProvider>(LLMProvider.OpenAI)
    val selectedProvider: StateFlow<LLMProvider> = _selectedProvider.asStateFlow()

    private val _currentApiKey = MutableStateFlow("")
    val currentApiKey: StateFlow<String> = _currentApiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow("")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    data class ProviderInfo(
        val displayName: String,
        val defaultModel: String,
        val fallbackModels: List<String>
    )

    val PROVIDER_INFO = mapOf(
        LLMProvider.OpenAI to ProviderInfo("OpenAI", "gpt-5-mini", listOf(
            "gpt-5", "gpt-5-mini", "gpt-5-nano",
            "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano",
            "gpt-4o", "gpt-4o-mini",
            "o3-mini", "o4-mini"
        )),
        LLMProvider.DeepSeek to ProviderInfo("DeepSeek", "deepseek-v4-flash", listOf(
            "deepseek-v4-flash", "deepseek-v4-pro"
        ))
    )

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _settingsState = MutableStateFlow(
        SettingsState(
            useAI = prefs.getBoolean("settings_use_ai", true),
            maxSubtasks = prefs.getInt("settings_max_subtasks", 5),
            aiDivisionStrategy = prefs.getString("settings_ai_strategy", null)
                ?.let { runCatching { DivisionStrategy.valueOf(it) }.getOrNull() }
                ?: DivisionStrategy.BALANCED,
            currentLanguage = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() ?: "auto"
        )
    )
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    fun updateLanguage(languageTag: String) {
        val appLocale: LocaleListCompat = if (languageTag == "auto") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
        _settingsState.update { it.copy(currentLanguage = languageTag) }
    }

    private val _appState = MutableStateFlow(
        AppState(
            executeAssistantActions = true
        )
    )
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    init {
        // Migrate old keys to provider-prefixed format
        val oldKey = prefs.getString("api_key", null)
        if (oldKey != null && prefs.getString("openai_api_key", null) == null) {
            prefs.edit().putString("openai_api_key", oldKey).remove("api_key").apply()
        }
        val oldModel = prefs.getString("selected_model", null)
        if (oldModel != null && prefs.getString("openai_selected_model", null) == null) {
            prefs.edit().putString("openai_selected_model", oldModel).remove("selected_model").apply()
        }

        val initialProviderStr = prefs.getString("selected_provider", null)
        val initialProvider = when (initialProviderStr) {
            "openai" -> LLMProvider.OpenAI
            "deepseek" -> LLMProvider.DeepSeek
            else -> LLMProvider.OpenAI
        }
        val initialInfo = PROVIDER_INFO[initialProvider] ?: PROVIDER_INFO[LLMProvider.OpenAI]!!

        val initialKey = loadApiKey(initialProvider)
        val initialModel = loadModel(initialProvider, initialInfo)

        todoAgent.setApiKey(initialProvider, initialKey)
        todoAgent.selectProvider(initialProvider)
        todoAgent.selectModelByName(initialModel)

        _selectedProvider.value = initialProvider
        _currentApiKey.value = initialKey
        _selectedModel.value = initialModel

        loadTasks()
        refreshModels()
        observeMemories()
    }

    private fun observeMemories() {
        viewModelScope.launch {
            longTermMemoryRepository.getAllMemories().collect { memories ->
                _settingsState.update { it.copy(longTermMemories = memories) }
            }
        }
    }

    private fun loadApiKey(provider: LLMProvider): String {
        val key = prefs.getString("${provider.id.lowercase()}_api_key", null)
        return key?.takeIf { it.isNotBlank() } ?: BuildConfig.OPENAI_API_KEY
    }

    private fun loadModel(provider: LLMProvider, info: ProviderInfo = PROVIDER_INFO[provider]!!): String {
        val model = prefs.getString("${provider.id.lowercase()}_selected_model", null)
        return model?.takeIf { it.isNotBlank() } ?: info.defaultModel
    }

    fun selectProvider(provider: LLMProvider) {
        val info = PROVIDER_INFO[provider] ?: return
        prefs.edit().putString("selected_provider", provider.id).apply()

        val key = loadApiKey(provider)
        val model = loadModel(provider, info)
        todoAgent.setApiKey(provider, key)
        todoAgent.selectProvider(provider)
        todoAgent.selectModelByName(model)

        _selectedProvider.value = provider
        _currentApiKey.value = key
        _selectedModel.value = model
        refreshModels()
    }

    fun setApiKey(provider: LLMProvider, key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("${provider.id.lowercase()}_api_key", trimmed).apply()
        if (_selectedProvider.value == provider) {
            todoAgent.setApiKey(provider, trimmed)
            _currentApiKey.value = trimmed
        }
    }

    fun selectModel(model: String) {
        val provider = _selectedProvider.value
        prefs.edit().putString("${provider.id.lowercase()}_selected_model", model).apply()
        todoAgent.selectModelByName(model)
        _selectedModel.value = model
    }

    fun refreshModels() {
        val info = PROVIDER_INFO[_selectedProvider.value]
        if (info != null) {
            _availableModels.value = info.fallbackModels
        }
    }

    /**
     * Debug-only developer setting: wipes all tasks and re-seeds sample data.
     * No-op in release builds (see [TodoRepository.resetSampleData]).
     */
    fun resetSampleData() {
        viewModelScope.launch {
            todoRepository.resetSampleData()
        }
    }

    fun updateSettings(settings: SettingsState) {
        prefs.edit()
            .putBoolean("settings_use_ai", settings.useAI)
            .putInt("settings_max_subtasks", settings.maxSubtasks)
            .putString("settings_ai_strategy", settings.aiDivisionStrategy.name)
            .apply()
        _settingsState.value = settings
    }

    private fun getSubtaskDivisionService(): SubtaskDivisionService {
        return SubtaskDivisionService(
            promptExecutor = todoAgent.buildExecutor(),
            model = todoAgent.getCurrentModel(),
            todoRepository = todoRepository
        )
    }

    private val json = Json { prettyPrint = true }

    // Debounce mechanism
    private var titleUpdateJob: Job? = null
    private var contentUpdateJob: Job? = null
    private val debounceDelayMs = 300L

    private fun loadTasks() {
        viewModelScope.launch {
            _appState.update { s -> s.copy(taskState = s.taskState.copy(isLoading = true)) }
            todoRepository.tasks.collect { tasks ->
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = currentState.taskState.copy(
                            items = tasks,
                            isLoading = false
                        )
                    )
                }
            }
        }
    }

    // --- CURRENT_TODO_STATE snapshot ---
    @Serializable
    private data class Snapshot(
        val now: String,
        val unfinished: List<Node>,
        val finished: List<Node>,
        val finished_count: Int
    )

    @Serializable
    private data class Node(
        val id: Long,
        val title: String,
        val status: String,
        val priority: String,
        val dueAt: String? = null,
        val children: List<Node> = emptyList(),
        val totalChildren: Int = 0,
        val doneChildren: Int = 0,
        val progress: Float = 0f
    )

    private fun buildCurrentTodoStateSnapshot(): String {
        val tasks = _appState.value.taskState.items
        val nowIso = Clock.System.now().toString()

        val openTasks = tasks.filter { it.status == TaskStatus.OPEN }
        val doneTasks = tasks.filter { it.status == TaskStatus.DONE }

        val openChildrenMap = openTasks.groupBy { it.parentId }
        val doneChildrenMap = doneTasks.groupBy { it.parentId }
        val allChildrenMap = tasks.groupBy { it.parentId }

        fun allDescendants(id: Long): List<Task> {
            val result = mutableListOf<Task>()
            fun dfs(currId: Long) {
                val kids = allChildrenMap[currId] ?: emptyList()
                for (k in kids) {
                    result += k
                    val kidId = k.id
                    if (kidId != null) dfs(kidId)
                }
            }
            dfs(id)
            return result
        }

        fun Task.toNode(childrenMap: Map<Long?, List<Task>>): Node {
            val childTasks = childrenMap[this.id] ?: emptyList()
            val childNodes = childTasks.map { it.toNode(childrenMap) }

            val myId = this.id ?: -1L
            val descendants = if (myId != -1L) allDescendants(myId) else emptyList()
            val totalChildren = descendants.size
            val doneChildren = descendants.count { it.status == TaskStatus.DONE }
            val progress = if (totalChildren > 0) doneChildren.toFloat() / totalChildren else 0f

            return Node(
                id = myId,
                title = this.title,
                status = this.status.name,
                priority = this.priority.name,
                dueAt = this.dueAt?.toString(),
                children = childNodes,
                totalChildren = totalChildren,
                doneChildren = doneChildren,
                progress = progress
            )
        }

        fun buildTree(rootCandidates: List<Task>, childrenMap: Map<Long?, List<Task>>): List<Node> {
            return rootCandidates.filter { it.parentId == null }.map { it.toNode(childrenMap) }
        }

        val unfinishedTree = buildTree(openTasks, openChildrenMap)
        val finishedTree = buildTree(doneTasks, doneChildrenMap)
        val snapshot = Snapshot(
            now = nowIso,
            unfinished = unfinishedTree,
            finished = finishedTree,
            finished_count = doneTasks.size
        )
        return json.encodeToString(snapshot)
    }

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.Task -> handleTaskEvent(event.event)
            is AppEvent.ChatOverlay -> handleChatOverlayEvent(event.event)
            is AppEvent.ManualAdd -> handleManualAddEvent(event.event)
            is AppEvent.DateTimePicker -> handleDateTimePickerEvent(event.event)
            is AppEvent.Priority -> handlePriorityEvent(event.event)
            is AppEvent.TaskDetail -> handleTaskDetailEvent(event.event)
            is AppEvent.SubtaskDivision -> handleSubtaskDivisionEvent(event.event)
            is AppEvent.LongTermMemory -> handleLongTermMemoryEvent(event.event)
            is AppEvent.SetExecuteAssistantActions -> {
                _appState.update { it.copy(executeAssistantActions = event.execute) }
            }
        }
    }

    private fun handleTaskEvent(event: me.superbear.todolist.ui.main.sections.tasks.TaskEvent) {
        when (event) {
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Toggle -> {
                toggleTaskPersist(event.task)
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Add -> {
                viewModelScope.launch {
                    val t = event.task
                    todoRepository.addTask(
                        title = t.title, parentId = t.parentId, content = t.content,
                        priority = t.priority, dueAt = t.dueAt, status = t.status
                    )
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Update -> {
                val t = event.task
                val id = t.id
                if (id != null) {
                    viewModelScope.launch {
                        todoRepository.updateTask(
                            id = id, title = t.title, content = t.content,
                            priority = t.priority, dueAt = t.dueAt
                        )
                    }
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Delete -> {
                viewModelScope.launch { todoRepository.deleteTask(event.taskId) }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.AddSubtask -> {
                if (event.order != null) {
                    todoRepository.insertTaskAt(title = event.title, parentId = event.parentId, order = event.order)
                } else {
                    todoRepository.addTask(title = event.title, parentId = event.parentId)
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.DeleteSubtask -> {
                viewModelScope.launch { todoRepository.deleteTask(event.subtaskId) }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.ToggleSubtask -> {
                todoRepository.toggleTaskStatus(event.childId, event.done)
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.UpdateSubtaskTitle -> {
                viewModelScope.launch {
                    todoRepository.updateTask(id = event.subtaskId, title = event.newTitle)
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.LoadTasks -> { loadTasks() }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.TasksLoaded -> {
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = currentState.taskState.copy(
                            items = event.tasks, isLoading = false
                        )
                    )
                }
            }
        }
    }

    private fun handleChatOverlayEvent(event: me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent) {
        when (event) {
            is me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent.SendMessage -> {
                handleSendChat(event.message)
            }
            else -> {
                _appState.update { currentState ->
                    currentState.copy(
                        chatOverlayState = ChatOverlayReducer.reduce(currentState.chatOverlayState, event)
                    )
                }
            }
        }
    }

    private fun handleManualAddEvent(event: me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent) {
        when (event) {
            is me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.Submit -> {
                handleManualAddSubmit(event.title, event.description)
            }
            else -> {
                _appState.update { currentState ->
                    currentState.copy(
                        manualAddState = ManualAddReducer.reduce(currentState.manualAddState, event)
                    )
                }
            }
        }
    }

    private fun handleDateTimePickerEvent(event: me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent) {
        when (event) {
            is me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent.SetDueDate -> {
                val detail = _appState.value.taskDetailState
                val taskId = detail.selectedTaskId
                if (detail.isVisible && taskId != null && event.timestamp != null) {
                    viewModelScope.launch {
                        try {
                            val dueAt = Instant.fromEpochMilliseconds(event.timestamp)
                            val updatedAt = Clock.System.now().toEpochMilliseconds()
                            todoRepository.updateDueAt(taskId, dueAt, updatedAt)
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Failed to update task due date: $taskId", e)
                        }
                    }
                }
                _appState.update { currentState ->
                    currentState.copy(
                        dateTimePickerState = DateTimePickerReducer.reduce(currentState.dateTimePickerState, event)
                    )
                }
            }
            else -> {
                _appState.update { currentState ->
                    currentState.copy(
                        dateTimePickerState = DateTimePickerReducer.reduce(currentState.dateTimePickerState, event)
                    )
                }
            }
        }
    }

    private fun handlePriorityEvent(event: me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent) {
        if (event is me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent.SetPriority) {
            val detail = _appState.value.taskDetailState
            val taskId = detail.selectedTaskId
            if (detail.isVisible && taskId != null) {
                viewModelScope.launch { todoRepository.updateTask(taskId, priority = event.priority) }
            }
        }
        _appState.update { currentState ->
            currentState.copy(
                priorityState = PriorityReducer.reduce(currentState.priorityState, event)
            )
        }
    }

    private fun handleTaskDetailEvent(event: TaskDetailEvent) {
        when (event) {
            is TaskDetailEvent.ShowDetail -> {
                val task = _appState.value.taskState.items.find { it.id == event.taskId }
                _appState.update {
                    it.copy(
                        taskDetailState = it.taskDetailState.copy(
                            isVisible = true, selectedTaskId = event.taskId,
                            editedTitle = task?.title ?: "", editedContent = task?.content ?: ""
                        )
                    )
                }
            }
            is TaskDetailEvent.HideDetail -> {
                _appState.update {
                    it.copy(
                        taskDetailState = it.taskDetailState.copy(
                            isVisible = false, selectedTaskId = null,
                            editedTitle = "", editedContent = ""
                        )
                    )
                }
            }
            is TaskDetailEvent.EditTitle -> {
                _appState.update {
                    it.copy(taskDetailState = it.taskDetailState.copy(editedTitle = event.title))
                }
                debouncedTitleUpdate(event.title)
            }
            is TaskDetailEvent.EditContent -> {
                _appState.update {
                    it.copy(taskDetailState = it.taskDetailState.copy(editedContent = event.content))
                }
                debouncedContentUpdate(event.content)
            }
            is TaskDetailEvent.UpdatePriority -> {
                viewModelScope.launch {
                    try {
                        val updatedAt = Clock.System.now().toEpochMilliseconds()
                        todoRepository.updatePriority(event.taskId, event.priority, updatedAt)
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to update task priority: ${event.taskId}", e)
                    }
                }
            }
            is TaskDetailEvent.DeleteTask -> {
                viewModelScope.launch {
                    try {
                        todoRepository.deleteTaskRecursively(event.taskId)
                        _appState.update {
                            it.copy(
                                taskDetailState = it.taskDetailState.copy(
                                    isVisible = false, selectedTaskId = null,
                                    editedTitle = "", editedContent = ""
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to delete task: ${event.taskId}", e)
                    }
                }
            }
        }
    }

    private fun handleSubtaskDivisionEvent(event: SubtaskDivisionEvent) {
        when (event) {
            is SubtaskDivisionEvent.GenerateSuggestions -> {
                generateSubtaskSuggestions(
                    taskId = event.taskId, strategy = event.strategy,
                    maxSubtasks = event.maxSubtasks, context = event.context, useAI = event.useAI
                )
            }
            is SubtaskDivisionEvent.CreateFromSuggestions -> {
                createSubtasksFromAI(
                    taskId = event.taskId, strategy = event.strategy,
                    maxSubtasks = event.maxSubtasks, context = event.context, useAI = event.useAI
                )
            }
            is SubtaskDivisionEvent.BatchDivide -> {
                batchDivideSubtasks(
                    taskIds = event.taskIds, strategy = event.strategy, useAI = event.useAI
                )
            }
        }
    }

    private fun generateSubtaskSuggestions(
        taskId: Long, strategy: DivisionStrategy?, maxSubtasks: Int?,
        context: String?, useAI: Boolean
    ) {
        val task = _appState.value.taskState.items.find { it.id == taskId }
        if (task == null) {
            Log.e("MainViewModel", "Task not found for subtask generation: $taskId")
            return
        }

        viewModelScope.launch {
            try {
                val result = getSubtaskDivisionService().generateSubtaskSuggestions(
                    task = task, strategy = strategy, maxSubtasks = maxSubtasks,
                    context = context, useAI = useAI
                )
                result.fold(
                    onSuccess = { response ->
                        Log.d("MainViewModel", "Generated ${response.subtasks.size} subtask suggestions")
                    },
                    onFailure = { error ->
                        Log.e("MainViewModel", "Failed to generate subtask suggestions", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Unexpected error in subtask generation", e)
            }
        }
    }

    private fun createSubtasksFromAI(
        taskId: Long, strategy: DivisionStrategy?, maxSubtasks: Int?,
        context: String?, useAI: Boolean
    ) {
        val task = _appState.value.taskState.items.find { it.id == taskId }
        if (task == null) {
            Log.e("MainViewModel", "Task not found for subtask creation: $taskId")
            return
        }

        viewModelScope.launch {
            try {
                _appState.update { currentState ->
                    currentState.copy(
                        taskDetailState = currentState.taskDetailState.copy(isSubtaskDivisionLoading = true)
                    )
                }
                val result = getSubtaskDivisionService().divideAndCreateSubtasks(
                    parentTask = task, strategy = strategy, maxSubtasks = maxSubtasks,
                    context = context, useAI = useAI, forceCreate = true
                )
                result.fold(
                    onSuccess = { divisionResult ->
                        Log.d("MainViewModel", "Successfully created ${divisionResult.createdTasks.size} subtasks")
                    },
                    onFailure = { error ->
                        Log.e("MainViewModel", "Failed to create subtasks", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Unexpected error in subtask creation", e)
            } finally {
                _appState.update { currentState ->
                    currentState.copy(
                        taskDetailState = currentState.taskDetailState.copy(isSubtaskDivisionLoading = false)
                    )
                }
            }
        }
    }

    private fun batchDivideSubtasks(
        taskIds: List<Long>, strategy: DivisionStrategy?, useAI: Boolean
    ) {
        val tasks = _appState.value.taskState.items.filter { it.id in taskIds }
        if (tasks.isEmpty()) {
            Log.e("MainViewModel", "No valid tasks found for batch division")
            return
        }

        viewModelScope.launch {
            try {
                val result = getSubtaskDivisionService().batchDivideAndCreate(
                    tasks = tasks, strategy = strategy, useAI = useAI
                )
                result.fold(
                    onSuccess = { results ->
                        val totalCreated = results.sumOf { it.createdTasks.size }
                        Log.d("MainViewModel", "Batch division completed: $totalCreated subtasks created")
                    },
                    onFailure = { error -> Log.e("MainViewModel", "Batch division failed", error) }
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Unexpected error in batch division", e)
            }
        }
    }

    private fun toggleTaskPersist(task: Task) {
        val newDone = task.status == TaskStatus.OPEN
        val id = task.id ?: return
        viewModelScope.launch { todoRepository.toggleTaskStatus(id, newDone) }
    }

    private fun debouncedTitleUpdate(newTitle: String) {
        val selectedTaskId = _appState.value.taskDetailState.selectedTaskId ?: return
        titleUpdateJob?.cancel()
        titleUpdateJob = viewModelScope.launch {
            delay(debounceDelayMs)
            val currentState = _appState.value.taskDetailState
            if (currentState.selectedTaskId == selectedTaskId) {
                val originalTask = _appState.value.taskState.items.find { it.id == selectedTaskId }
                if (originalTask != null && originalTask.title != newTitle) {
                    val updatedAt = Clock.System.now().toEpochMilliseconds()
                    todoRepository.updateTitle(selectedTaskId, newTitle, updatedAt)
                }
            }
        }
    }

    private fun debouncedContentUpdate(newContent: String) {
        val selectedTaskId = _appState.value.taskDetailState.selectedTaskId ?: return
        contentUpdateJob?.cancel()
        contentUpdateJob = viewModelScope.launch {
            delay(debounceDelayMs)
            val currentState = _appState.value.taskDetailState
            if (currentState.selectedTaskId == selectedTaskId) {
                val originalTask = _appState.value.taskState.items.find { it.id == selectedTaskId }
                if (originalTask != null && originalTask.content != newContent) {
                    val updatedAt = Clock.System.now().toEpochMilliseconds()
                    todoRepository.updateContent(selectedTaskId, newContent.takeIf { it.isNotBlank() }, updatedAt)
                }
            }
        }
    }

    private fun handleManualAddSubmit(title: String, description: String?) {
        val currentState = _appState.value
        val dueAtInstant = currentState.dateTimePickerState.selectedDueDateMs?.let { timestamp ->
            Instant.fromEpochMilliseconds(timestamp)
        }

        viewModelScope.launch {
            try {
                todoRepository.addTask(
                    title = title, parentId = null, content = description,
                    priority = currentState.priorityState.selectedPriority, dueAt = dueAtInstant
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to add task: $title", e)
            }
        }
        resetManualForm()
    }

    private fun resetManualForm() {
        _appState.update { currentState ->
            currentState.copy(
                manualAddState = ManualAddReducer.reduce(
                    currentState.manualAddState,
                    me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.Reset
                ),
                dateTimePickerState = DateTimePickerReducer.reduce(
                    currentState.dateTimePickerState,
                    me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent.Close
                ).copy(selectedDueDateMs = null),
                priorityState = PriorityReducer.reduce(
                    currentState.priorityState,
                    me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent.SetPriority(Priority.DEFAULT)
                )
            )
        }
    }

    // Chat Handling — uses Koog TodoAgent
    private fun handleSendChat(message: String) {
        val userMessage = ChatMessage(
            text = message, sender = Sender.User,
            timestamp = Clock.System.now(), status = MessageStatus.Sent
        )
        val assistantMessage = ChatMessage(
            sender = Sender.Assistant, text = "...",
            timestamp = Clock.System.now(), status = MessageStatus.Sending,
            replyToId = userMessage.id
        )

        _appState.update { currentState ->
            currentState.copy(
                chatOverlayState = ChatOverlayReducer.addMessages(
                    currentState.chatOverlayState, userMessage, assistantMessage
                )
            )
        }

        viewModelScope.launch {
            sendToAssistant(message, assistantMessage)
        }
    }

    private suspend fun sendToAssistant(message: String, placeholderMessage: ChatMessage) {
        val memoryContext = longTermMemoryRepository.getMemoryContextForAI()
        val todoState = buildCurrentTodoStateSnapshot()

        val result = todoAgent.chat(
            userMessage = message,
            history = _appState.value.chatOverlayState.messages,
            todoState = todoState,
            memoryContext = memoryContext,
            language = Locale.getDefault().language
        )

        result.onSuccess { responseText ->
            if (responseText.isNotBlank()) {
                val newMessage = placeholderMessage.copy(
                    text = responseText, status = MessageStatus.Sent
                )
                _appState.update { currentState ->
                    currentState.copy(
                        chatOverlayState = ChatOverlayReducer.replaceMessage(
                            currentState.chatOverlayState, placeholderMessage.id, newMessage
                        )
                    )
                }
            } else {
                _appState.update { currentState ->
                    currentState.copy(
                        chatOverlayState = ChatOverlayReducer.removeMessage(
                            currentState.chatOverlayState, placeholderMessage.id
                        )
                    )
                }
            }
        }.onFailure { error ->
            handleAssistantError(error, placeholderMessage)
        }
    }

    private fun handleAssistantError(error: Throwable, placeholderMessage: ChatMessage) {
        Log.e("MainViewModel", "Error sending message", error)
        val errorMessage = placeholderMessage.copy(
            text = getApplication<Application>().getString(R.string.error_fetch_response),
            status = MessageStatus.Failed
        )
        _appState.update { currentState ->
            currentState.copy(
                chatOverlayState = ChatOverlayReducer.replaceMessage(
                    currentState.chatOverlayState, placeholderMessage.id, errorMessage
                )
            )
        }
    }

    fun getChildren(parentId: Long): List<Task> {
        return todoRepository.getChildrenFromFlow(parentId)
    }

    fun getParentProgress(parentId: Long): Pair<Int, Int> {
        return todoRepository.getParentProgressFromFlow(parentId)
    }

    fun getSelectedTask(): Task? {
        val selectedTaskId = _appState.value.taskDetailState.selectedTaskId
        return if (selectedTaskId != null) {
            _appState.value.taskState.items.find { it.id == selectedTaskId }
        } else null
    }

    private fun handleLongTermMemoryEvent(event: LongTermMemoryEvent) {
        when (event) {
            is LongTermMemoryEvent.AddMemory -> {
                viewModelScope.launch {
                    longTermMemoryRepository.createMemory(
                        content = event.content,
                        category = event.category,
                        importance = event.importance,
                        isActive = event.isActive
                    )
                }
            }
            is LongTermMemoryEvent.EditMemory -> {
                viewModelScope.launch {
                    longTermMemoryRepository.updateMemory(
                        id = event.memory.id,
                        content = event.content,
                        category = event.category,
                        importance = event.importance,
                        isActive = event.isActive
                    )
                }
            }
            is LongTermMemoryEvent.DeleteMemory -> {
                viewModelScope.launch {
                    longTermMemoryRepository.deleteMemory(event.memory.id)
                }
            }
            is LongTermMemoryEvent.ToggleMemoryActive -> {
                viewModelScope.launch {
                    longTermMemoryRepository.toggleMemoryActive(event.memory.id, event.isActive)
                }
            }
            is LongTermMemoryEvent.LoadMemories -> {
                // Flow based, no explicit load needed if UI collects
            }
        }
    }

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
                MainViewModel(
                    application = application,
                    todoRepository = todoRepository,
                    longTermMemoryRepository = longTermMemoryRepository,
                    todoAgent = TodoAgent(todoRepository, longTermMemoryRepository)
                )
            }
        }
    }
}
