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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import me.superbear.todolist.ui.main.state.TaskDetailEvent
import me.superbear.todolist.ui.main.state.SubtaskDivisionEvent
import me.superbear.todolist.ui.main.state.LongTermMemoryEvent
import me.superbear.todolist.ui.main.state.AppEvent
import me.superbear.todolist.ui.main.state.AppState
import me.superbear.todolist.assistant.subtask.SubtaskDivisionService
import me.superbear.todolist.data.repository.LongTermMemoryRepository

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
    private val realAssistantClient = RealAssistantClient()
    private val mockAssistantClient = MockAssistantClient()
    private val assistantController = AssistantController(
        mockAssistantClient = mockAssistantClient,
        realAssistantClient = realAssistantClient
    )
    private val subtaskDivisionService = SubtaskDivisionService(
        assistantClient = if (BuildConfig.USE_MOCK_ASSISTANT) mockAssistantClient else realAssistantClient,
        todoRepository = todoRepository
    )
    private val json = Json { prettyPrint = true }
    
    // 防抖机制：避免过于频繁的数据库写入
    private var titleUpdateJob: Job? = null
    private var contentUpdateJob: Job? = null
    private val debounceDelayMs = 300L // 300ms 防抖延迟
    
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
            is AppEvent.TaskDetail -> handleTaskDetailEvent(event.event)
            is AppEvent.SubtaskDivision -> handleSubtaskDivisionEvent(event.event)
            is AppEvent.LongTermMemory -> handleLongTermMemoryEvent(event.event)
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
                Log.d("MainViewModel", "Adding subtask: ${event.title} to parent: ${event.parentId}, order: ${event.order}")
                if (event.order != null) {
                    // Insert at specific position
                    todoRepository.insertTaskAt(
                        title = event.title,
                        parentId = event.parentId,
                        order = event.order
                    )
                } else {
                    // Add at the end (default behavior)
                    todoRepository.addTask(
                        title = event.title,
                        parentId = event.parentId
                    )
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.DeleteSubtask -> {
                Log.d("MainViewModel", "Deleting subtask: ${event.subtaskId}")
                viewModelScope.launch {
                    todoRepository.deleteTask(event.subtaskId)
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.ToggleSubtask -> {
                todoRepository.toggleTaskStatus(event.childId, event.done)
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.UpdateSubtaskTitle -> {
                viewModelScope.launch {
                    todoRepository.updateTask(id = event.subtaskId, title = event.newTitle)
                }
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
                // If task detail is open, apply directly to the selected task
                val detail = _appState.value.taskDetailState
                val taskId = detail.selectedTaskId
                if (detail.isVisible && taskId != null && event.timestamp != null) {
                    viewModelScope.launch {
                        try {
                            val dueAt = Instant.fromEpochMilliseconds(event.timestamp)
                            val updatedAt = Clock.System.now().toEpochMilliseconds()
                            todoRepository.updateDueAt(taskId, dueAt, updatedAt)
                            Log.d("MainViewModel", "Updated task due date: $taskId -> $dueAt")
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
                val id = taskId
                viewModelScope.launch { todoRepository.updateTask(id, priority = event.priority) }
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
                            isVisible = true, 
                            selectedTaskId = event.taskId,
                            editedTitle = task?.title ?: "",
                            editedContent = task?.content ?: ""
                        )
                    ) 
                }
            }
            is TaskDetailEvent.HideDetail -> {
                _appState.update { 
                    it.copy(
                        taskDetailState = it.taskDetailState.copy(
                            isVisible = false, 
                            selectedTaskId = null,
                            editedTitle = "",
                            editedContent = ""
                        )
                    ) 
                }
            }
            is TaskDetailEvent.EditTitle -> {
                // 立即更新UI状态
                _appState.update { 
                    it.copy(
                        taskDetailState = it.taskDetailState.copy(editedTitle = event.title)
                    ) 
                }
                // 随输随存：防抖保存到数据库
                debouncedTitleUpdate(event.title)
            }
            is TaskDetailEvent.EditContent -> {
                // 立即更新UI状态
                _appState.update { 
                    it.copy(
                        taskDetailState = it.taskDetailState.copy(editedContent = event.content)
                    ) 
                }
                // 随输随存：防抖保存到数据库
                debouncedContentUpdate(event.content)
            }
            is TaskDetailEvent.UpdatePriority -> {
                // 更新任务优先级
                viewModelScope.launch {
                    try {
                        val updatedAt = Clock.System.now().toEpochMilliseconds()
                        todoRepository.updatePriority(event.taskId, event.priority, updatedAt)
                        Log.d("MainViewModel", "Updated task priority: ${event.taskId} -> ${event.priority}")
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to update task priority: ${event.taskId}", e)
                    }
                }
            }
            is TaskDetailEvent.DeleteTask -> {
                // 递归删除任务及其所有子任务
                viewModelScope.launch {
                    try {
                        todoRepository.deleteTaskRecursively(event.taskId)
                        Log.d("MainViewModel", "Recursively deleted task: ${event.taskId}")
                        
                        // 删除后自动关闭详情页面
                        _appState.update { 
                            it.copy(
                                taskDetailState = it.taskDetailState.copy(
                                    isVisible = false,
                                    selectedTaskId = null,
                                    editedTitle = "",
                                    editedContent = ""
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
                    taskId = event.taskId,
                    strategy = event.strategy,
                    maxSubtasks = event.maxSubtasks,
                    context = event.context,
                    useAI = event.useAI
                )
            }
            is SubtaskDivisionEvent.CreateFromSuggestions -> {
                createSubtasksFromAI(
                    taskId = event.taskId,
                    strategy = event.strategy,
                    maxSubtasks = event.maxSubtasks,
                    context = event.context,
                    useAI = event.useAI
                )
            }
            is SubtaskDivisionEvent.BatchDivide -> {
                batchDivideSubtasks(
                    taskIds = event.taskIds,
                    strategy = event.strategy,
                    useAI = event.useAI
                )
            }
        }
    }

    private fun generateSubtaskSuggestions(
        taskId: Long,
        strategy: me.superbear.todolist.assistant.subtask.DivisionStrategy?,
        maxSubtasks: Int?,
        context: String?,
        useAI: Boolean
    ) {
        val task = _appState.value.taskState.items.find { it.id == taskId }
        if (task == null) {
            Log.e("MainViewModel", "Task not found for subtask generation: $taskId")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Generating subtask suggestions for: ${task.title}")
                val result = subtaskDivisionService.generateSubtaskSuggestions(
                    task = task,
                    strategy = strategy,
                    maxSubtasks = maxSubtasks,
                    context = context,
                    useAI = useAI
                )
                
                result.fold(
                    onSuccess = { response ->
                        Log.d("MainViewModel", "Generated ${response.subtasks.size} subtask suggestions")
                        // TODO: 可以在这里添加UI状态更新，显示建议给用户确认
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
        taskId: Long,
        strategy: me.superbear.todolist.assistant.subtask.DivisionStrategy?,
        maxSubtasks: Int?,
        context: String?,
        useAI: Boolean
    ) {
        val task = _appState.value.taskState.items.find { it.id == taskId }
        if (task == null) {
            Log.e("MainViewModel", "Task not found for subtask creation: $taskId")
            return
        }

        viewModelScope.launch {
            try {
                // 设置加载状态为true
                _appState.update { currentState ->
                    currentState.copy(
                        taskDetailState = currentState.taskDetailState.copy(
                            isSubtaskDivisionLoading = true
                        )
                    )
                }
                
                Log.d("MainViewModel", "Creating subtasks for: ${task.title}")
                val result = subtaskDivisionService.divideAndCreateSubtasks(
                    parentTask = task,
                    strategy = strategy,
                    maxSubtasks = maxSubtasks,
                    context = context,
                    useAI = useAI,
                    forceCreate = true
                )
                
                result.fold(
                    onSuccess = { divisionResult ->
                        Log.d("MainViewModel", "Successfully created ${divisionResult.createdTasks.size} subtasks")
                        // UI will automatically update through Room Flow
                    },
                    onFailure = { error ->
                        Log.e("MainViewModel", "Failed to create subtasks", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Unexpected error in subtask creation", e)
            } finally {
                // 无论成功还是失败，都清除加载状态
                _appState.update { currentState ->
                    currentState.copy(
                        taskDetailState = currentState.taskDetailState.copy(
                            isSubtaskDivisionLoading = false
                        )
                    )
                }
            }
        }
    }

    private fun batchDivideSubtasks(
        taskIds: List<Long>,
        strategy: me.superbear.todolist.assistant.subtask.DivisionStrategy?,
        useAI: Boolean
    ) {
        val tasks = _appState.value.taskState.items.filter { it.id in taskIds }
        if (tasks.isEmpty()) {
            Log.e("MainViewModel", "No valid tasks found for batch division")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Batch dividing ${tasks.size} tasks")
                val result = subtaskDivisionService.batchDivideAndCreate(
                    tasks = tasks,
                    strategy = strategy,
                    useAI = useAI
                )
                
                result.fold(
                    onSuccess = { results ->
                        val totalCreated = results.sumOf { it.createdTasks.size }
                        Log.d("MainViewModel", "Batch division completed: $totalCreated subtasks created")
                    },
                    onFailure = { error ->
                        Log.e("MainViewModel", "Batch division failed", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Unexpected error in batch division", e)
            }
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


    /**
     * 防抖更新标题：取消之前的更新任务，延迟后执行新的更新
     */
    private fun debouncedTitleUpdate(newTitle: String) {
        val selectedTaskId = _appState.value.taskDetailState.selectedTaskId ?: return
        
        // 取消之前的更新任务
        titleUpdateJob?.cancel()
        
        // 启动新的防抖更新任务
        titleUpdateJob = viewModelScope.launch {
            delay(debounceDelayMs)
            
            // 检查任务是否仍然选中且标题确实有变化
            val currentState = _appState.value.taskDetailState
            if (currentState.selectedTaskId == selectedTaskId) {
                val originalTask = _appState.value.taskState.items.find { it.id == selectedTaskId }
                if (originalTask != null && originalTask.title != newTitle) {
                    val updatedAt = Clock.System.now().toEpochMilliseconds()
                    todoRepository.updateTitle(selectedTaskId, newTitle, updatedAt)
                    Log.d("MainViewModel", "Auto-saved title: $newTitle")
                }
            }
        }
    }

    /**
     * 防抖更新内容：取消之前的更新任务，延迟后执行新的更新
     */
    private fun debouncedContentUpdate(newContent: String) {
        val selectedTaskId = _appState.value.taskDetailState.selectedTaskId ?: return
        
        // 取消之前的更新任务
        contentUpdateJob?.cancel()
        
        // 启动新的防抖更新任务
        contentUpdateJob = viewModelScope.launch {
            delay(debounceDelayMs)
            
            // 检查任务是否仍然选中且内容确实有变化
            val currentState = _appState.value.taskDetailState
            if (currentState.selectedTaskId == selectedTaskId) {
                val originalTask = _appState.value.taskState.items.find { it.id == selectedTaskId }
                if (originalTask != null && originalTask.content != newContent) {
                    val updatedAt = Clock.System.now().toEpochMilliseconds()
                    todoRepository.updateContent(selectedTaskId, newContent.takeIf { it.isNotBlank() }, updatedAt)
                    Log.d("MainViewModel", "Auto-saved content")
                }
            }
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
        // TODO: 获取长期记忆上下文
        val memoryContext = "" // 暂时为空，后续需要从LongTermMemoryRepository获取
        
        val result = assistantController.send(
            message,
            _appState.value.chatOverlayState.messages,
            _appState.value.useMockAssistant,
            memoryContext
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
                
                // Get current task to check what needs updating
                val currentTask = todoRepository.getTaskById(id)
                if (currentTask == null) {
                    Log.w("MainViewModel", "Task not found for update: $id")
                    return
                }
                
                // Handle status changes separately using the dedicated method
                if (currentTask.status != task.status) {
                    val isDone = task.status == TaskStatus.DONE
                    todoRepository.toggleTaskStatus(id, isDone)
                    Log.d("MainViewModel", "Updated task status: $id -> ${task.status}")
                }
                
                // Check if any other fields need updating
                val needsFieldUpdate = currentTask.title != task.title ||
                    currentTask.content != task.content ||
                    currentTask.priority != task.priority ||
                    currentTask.dueAt != task.dueAt
                
                // Only call updateTask if other fields actually changed
                if (needsFieldUpdate) {
                    todoRepository.updateTask(
                        id = id,
                        title = task.title,
                        content = task.content,
                        priority = task.priority,
                        dueAt = task.dueAt
                    )
                    Log.d("MainViewModel", "Updated task fields: $id")
                }
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

    /**
     * Get the currently selected task for the task detail sheet
     */
    fun getSelectedTask(): Task? {
        val selectedTaskId = _appState.value.taskDetailState.selectedTaskId
        return if (selectedTaskId != null) {
            _appState.value.taskState.items.find { it.id == selectedTaskId }
        } else {
            null
        }
    }

    /**
     * 处理长期记忆事件
     */
    private fun handleLongTermMemoryEvent(event: LongTermMemoryEvent) {
        when (event) {
            is LongTermMemoryEvent.AddMemory -> {
                viewModelScope.launch {
                    try {
                        // 这里需要注入LongTermMemoryRepository
                        // 暂时添加日志，后续需要实现依赖注入
                        Log.d("MainViewModel", "Add memory: ${event.content}")
                        // longTermMemoryRepository.createMemory(
                        //     content = event.content,
                        //     category = event.category,
                        //     importance = event.importance,
                        //     isActive = event.isActive
                        // )
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error adding memory", e)
                    }
                }
            }
            is LongTermMemoryEvent.EditMemory -> {
                viewModelScope.launch {
                    try {
                        Log.d("MainViewModel", "Edit memory: ${event.memory.id}")
                        // longTermMemoryRepository.updateMemory(
                        //     id = event.memory.id!!,
                        //     content = event.content,
                        //     category = event.category,
                        //     importance = event.importance,
                        //     isActive = event.isActive
                        // )
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error editing memory", e)
                    }
                }
            }
            is LongTermMemoryEvent.DeleteMemory -> {
                viewModelScope.launch {
                    try {
                        Log.d("MainViewModel", "Delete memory: ${event.memory.id}")
                        // longTermMemoryRepository.deleteMemory(event.memory.id!!)
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error deleting memory", e)
                    }
                }
            }
            is LongTermMemoryEvent.ToggleMemoryActive -> {
                viewModelScope.launch {
                    try {
                        Log.d("MainViewModel", "Toggle memory active: ${event.memory.id} -> ${event.isActive}")
                        // longTermMemoryRepository.toggleMemoryActive(event.memory.id!!, event.isActive)
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error toggling memory active", e)
                    }
                }
            }
            is LongTermMemoryEvent.LoadMemories -> {
                viewModelScope.launch {
                    try {
                        Log.d("MainViewModel", "Load memories")
                        // val memories = longTermMemoryRepository.getAllMemories()
                        // 更新设置状态中的记忆列表
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error loading memories", e)
                    }
                }
            }
        }
    }
}
