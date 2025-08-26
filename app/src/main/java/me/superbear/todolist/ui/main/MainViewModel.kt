package me.superbear.todolist.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.superbear.todolist.assistant.AssistantActionParser
import me.superbear.todolist.assistant.MockAssistantClient
import me.superbear.todolist.assistant.RealAssistantClient
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.domain.entities.ChatMessage
import me.superbear.todolist.domain.entities.MessageStatus
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.domain.entities.Sender
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus
import me.superbear.todolist.ui.main.sections.chatOverlay.AssistantActionHooks
import me.superbear.todolist.ui.main.sections.chatOverlay.AssistantController
import me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayReducer
import me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerReducer
import me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddReducer
import me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityReducer
import me.superbear.todolist.ui.main.state.AppEvent
import me.superbear.todolist.ui.main.state.AppState

/**
 * Refactored MainViewModel - Thin orchestrator that routes events to reducers
 * and handles side effects (I/O, async operations).
 *
 * Key changes:
 * - Uses AppState instead of UiState (composed of sub-domain states)
 * - Routes AppEvent to appropriate reducers
 * - Delegates assistant logic to AssistantController
 * - Maintains single-screen UX while improving separation of concerns
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val todoRepository = TodoRepository(application)
    private val assistantController = AssistantController(
        mockAssistantClient = MockAssistantClient(),
        realAssistantClient = RealAssistantClient(),
        assistantActionParser = AssistantActionParser()
    )
    private val json = Json { prettyPrint = true }
    
    // Root state using new AppState structure
    private val _appState = MutableStateFlow(
        AppState(
            useMockAssistant = BuildConfig.USE_MOCK_ASSISTANT,
            executeAssistantActions = true
        )
    )
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    init {
        loadTasks()
        setupAssistantStateProvider()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            // 显示加载中
            _appState.update { s -> s.copy(taskState = s.taskState.copy(isLoading = true)) }
            // 观察 Repository 的任务变化，直接写入 UI 状态（DB 为唯一数据源）
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

    private fun setupAssistantStateProvider() {
        // Provide CURRENT_TODO_STATE to the real assistant client.
        assistantController.setStateProvider {
            buildCurrentTodoStateSnapshot()
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

        // Build parent->children maps for both statuses (for children lists)
        val openTasks = tasks.filter { it.status == TaskStatus.OPEN }
        val doneTasks = tasks.filter { it.status == TaskStatus.DONE }

        val openChildrenMap = openTasks.groupBy { it.parentId }
        val doneChildrenMap = doneTasks.groupBy { it.parentId }

        // Also build a map across ALL tasks to compute totals/progress
        val allChildrenMap = tasks.groupBy { it.parentId }
        val byId = tasks.associateBy { it.id }

        fun Task.priorityString(): String = this.priority.name

        // Count all descendants for totals
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
                priority = this.priorityString(),
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

    /**
     * Main event handler - routes events to appropriate reducers and handles side effects
     */
    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.Task -> handleTaskEvent(event.event)
            is AppEvent.ChatOverlay -> handleChatOverlayEvent(event.event)
            is AppEvent.ManualAdd -> handleManualAddEvent(event.event)
            is AppEvent.DateTimePicker -> handleDateTimePickerEvent(event.event)
            is AppEvent.Priority -> handlePriorityEvent(event.event)
            is AppEvent.SetUseMockAssistant -> {
                _appState.update { it.copy(useMockAssistant = event.useMock) }
            }
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
                // Persist add to DB; rely on Room Flow to update UI
                viewModelScope.launch {
                    val t = event.task
                    todoRepository.addTask(
                        title = t.title,
                        parentId = t.parentId,
                        content = t.content,
                        priority = t.priority,
                        dueAt = t.dueAt,
                        status = t.status
                    )
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Update -> {
                // Persist update to DB; rely on Room Flow to update UI
                val t = event.task
                val id = t.id
                if (id != null) {
                    viewModelScope.launch {
                        todoRepository.updateTask(
                            id = id,
                            title = t.title,
                            content = t.content,
                            priority = t.priority,
                            dueAt = t.dueAt
                        )
                    }
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Delete -> {
                // Persist delete to DB; rely on Room Flow to update UI
                viewModelScope.launch {
                    todoRepository.deleteTask(event.taskId)
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.AddSubtask -> {
                todoRepository.addTask(title = event.title, parentId = event.parentId)
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.ToggleSubtask -> {
                todoRepository.toggleTaskStatus(event.childId, event.done)
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.LoadTasks -> {
                loadTasks()
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.TasksLoaded -> {
                // 直接更新 UI 状态
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = currentState.taskState.copy(
                            items = event.tasks,
                            isLoading = false
                        )
                    )
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.ShowDetail -> {
                // Show task detail sheet
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = currentState.taskState.copy(
                            selectedTaskId = event.taskId,
                            showDetail = true
                        )
                    )
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.HideDetail -> {
                // Hide task detail sheet
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = currentState.taskState.copy(
                            selectedTaskId = null,
                            showDetail = false
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
        _appState.update { currentState ->
            currentState.copy(
                dateTimePickerState = DateTimePickerReducer.reduce(currentState.dateTimePickerState, event)
            )
        }
    }

    private fun handlePriorityEvent(event: me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent) {
        _appState.update { currentState ->
            currentState.copy(
                priorityState = PriorityReducer.reduce(currentState.priorityState, event)
            )
        }
    }

    // Task Management: persist first, UI listens to DB Flow
    private fun toggleTaskPersist(task: Task) {
        val newDone = task.status == TaskStatus.OPEN
        val id = task.id ?: return
        viewModelScope.launch {
            todoRepository.toggleTaskStatus(id, newDone)
        }
    }


    // Removed obsolete optimistic subtask toggle; DB is the single source of truth.

    private fun handleManualAddSubmit(title: String, description: String?) {
        val currentState = _appState.value
        val dueAtInstant = currentState.dateTimePickerState.selectedDueDateMs?.let { timestamp ->
            Instant.fromEpochMilliseconds(timestamp)
        }

        // Add minimal task to database; Room will auto-generate the ID.
        // 现在一并持久化描述/优先级/截止时间。
        viewModelScope.launch {
            try {
                todoRepository.addTask(
                    title = title,
                    parentId = null,
                    content = description,
                    priority = currentState.priorityState.selectedPriority,
                    dueAt = dueAtInstant
                )
                Log.d("MainViewModel", "Task added successfully: $title")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to add task: $title", e)
            }
        }

        // Reset manual add form
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

    // Chat Handling
    private fun handleSendChat(message: String) {
        val userMessage = ChatMessage(
            text = message,
            sender = Sender.User,
            timestamp = Clock.System.now(),
            status = MessageStatus.Sent
        )
        val assistantMessage = ChatMessage(
            sender = Sender.Assistant,
            text = "...",
            timestamp = Clock.System.now(),
            status = MessageStatus.Sending,
            replyToId = userMessage.id
        )
        
        // Add messages using reducer
        _appState.update { currentState ->
            currentState.copy(
                chatOverlayState = ChatOverlayReducer.addMessages(
                    currentState.chatOverlayState,
                    userMessage,
                    assistantMessage
                )
            )
        }

        viewModelScope.launch {
            sendToAssistant(message, assistantMessage)
        }
    }

    private suspend fun sendToAssistant(message: String, placeholderMessage: ChatMessage) {
        val result = assistantController.send(
            message,
            _appState.value.chatOverlayState.messages,
            _appState.value.useMockAssistant
        )
        
        result.onSuccess { envelope ->
            handleAssistantResponse(envelope, placeholderMessage)
        }.onFailure { error ->
            handleAssistantError(error, placeholderMessage)
        }
    }

    private fun handleAssistantResponse(envelope: me.superbear.todolist.assistant.AssistantEnvelope, placeholderMessage: ChatMessage) {
        // Handle assistant's text response
        if (!envelope.say.isNullOrBlank()) {
            val newAssistantMessage = placeholderMessage.copy(
                text = envelope.say,
                status = MessageStatus.Sent
            )
            _appState.update { currentState ->
                currentState.copy(
                    chatOverlayState = ChatOverlayReducer.replaceMessage(
                        currentState.chatOverlayState,
                        placeholderMessage.id,
                        newAssistantMessage
                    )
                )
            }
        } else {
            _appState.update { currentState ->
                currentState.copy(
                    chatOverlayState = ChatOverlayReducer.removeMessage(
                        currentState.chatOverlayState,
                        placeholderMessage.id
                    )
                )
            }
        }

        // Handle assistant's actions
        if (envelope.actions.isNotEmpty()) {
            if (_appState.value.executeAssistantActions) {
                executeAssistantActions(envelope.actions)
            }
        }
    }

    private fun handleAssistantError(error: Throwable, placeholderMessage: ChatMessage) {
        Log.e("MainViewModel", "Error sending message", error)
        val errorMessage = placeholderMessage.copy(
            text = "[Error: unable to fetch response]",
            status = MessageStatus.Failed
        )
        _appState.update { currentState ->
            currentState.copy(
                chatOverlayState = ChatOverlayReducer.replaceMessage(
                    currentState.chatOverlayState,
                    placeholderMessage.id,
                    errorMessage
                )
            )
        }
    }

    private fun executeAssistantActions(actions: List<me.superbear.todolist.assistant.AssistantAction>) {
        viewModelScope.launch {
            assistantController.executeActions(
                actions,
                Clock.System.now(),
                createAssistantActionHooks()
            )
        }
    }

    private fun createAssistantActionHooks(): AssistantActionHooks {
        return object : AssistantActionHooks {
            override suspend fun addTask(task: Task) {
                // Persist via repository with full fields; UI updates through Room Flow
                todoRepository.addTask(
                    title = task.title,
                    parentId = task.parentId,
                    content = task.content,
                    priority = task.priority,
                    dueAt = task.dueAt,
                    status = task.status
                )
            }

            override suspend fun updateTask(task: Task) {
                val id = task.id ?: return
                todoRepository.updateTask(
                    id = id,
                    title = task.title,
                    content = task.content,
                    priority = task.priority,
                    dueAt = task.dueAt
                )
            }

            override suspend fun deleteTask(taskId: Long) {
                todoRepository.deleteTask(taskId)
            }

            override suspend fun moveTaskToParent(taskId: Long, newParentId: Long?, newIndex: Long?) {
                todoRepository.moveTaskToParent(taskId, newParentId, newIndex)
            }

            override suspend fun getTask(taskId: Long): Task? {
                return todoRepository.getTaskById(taskId)
            }

            override suspend fun getChildren(parentId: Long): List<Task> {
                return todoRepository.getChildrenFromDb(parentId)
            }
        }
    }

    // Utility methods for task relationships - delegate to repository
    fun getChildren(parentId: Long): List<Task> {
        return todoRepository.getChildrenFromFlow(parentId)
    }

    fun getParentProgress(parentId: Long): Pair<Int, Int> {
        return todoRepository.getParentProgressFromFlow(parentId)
    }
}
