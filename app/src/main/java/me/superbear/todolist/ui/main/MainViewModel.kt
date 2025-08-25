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
import me.superbear.todolist.ui.main.sections.tasks.TaskReducer
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
            // 观察 Repository 的任务变化，通过 TaskReducer 更新状态
            // 这样保持了架构的一致性，避免 MainViewModel 过于臃肿
            todoRepository.tasks.collect { tasks ->
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = TaskReducer.reduce(
                            currentState.taskState,
                            me.superbear.todolist.ui.main.sections.tasks.TaskEvent.TasksLoaded(tasks)
                        )
                    )
                }
            }
        }
    }

    private fun setupAssistantStateProvider() {
        // Set up state provider for assistant to access current task state
        // This would need to be implemented in AssistantController or RealAssistantClient
        // For now, we'll handle this through the existing mechanism
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
                toggleTaskWithOptimisticUpdate(event.task)
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Add -> {
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = TaskReducer.reduce(currentState.taskState, event)
                    )
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Update -> {
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = TaskReducer.reduce(currentState.taskState, event)
                    )
                }
            }
            is me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Delete -> {
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = TaskReducer.reduce(currentState.taskState, event)
                    )
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
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = TaskReducer.reduce(currentState.taskState, event)
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

    // Task Management with Side Effects
    private fun toggleTaskWithOptimisticUpdate(task: Task) {
        val originalStatus = task.status
        val newStatus = if (originalStatus == TaskStatus.OPEN) TaskStatus.DONE else TaskStatus.OPEN
        val updatedTask = task.copy(status = newStatus)

        // Optimistic update
        _appState.update { currentState ->
            currentState.copy(
                taskState = TaskReducer.reduce(
                    currentState.taskState,
                    me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Update(updatedTask)
                )
            )
        }

        // Server update with revert on failure
        viewModelScope.launch {
            val success = todoRepository.updateTaskOnServer(updatedTask)
            if (!success) {
                // Revert on failure
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = TaskReducer.reduce(
                            currentState.taskState,
                            me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Update(task)
                        )
                    )
                }
            }
        }
    }

    private fun handleAddSubtask(parentId: Long, title: String): Task {
        val nextOrder = (getChildren(parentId).map { it.orderInParent }.maxOrNull() ?: -1L) + 1L
        val newTask = Task(
            id = System.currentTimeMillis(),
            title = title,
            createdAt = Clock.System.now(),
            status = TaskStatus.OPEN,
            parentId = parentId,
            orderInParent = nextOrder
        )
        
        _appState.update { currentState ->
            currentState.copy(
                taskState = TaskReducer.reduce(
                    currentState.taskState,
                    me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Add(newTask)
                )
            )
        }
        return newTask
    }

    private fun toggleSubtaskWithOptimisticUpdate(childId: Long, done: Boolean) {
        val current = _appState.value.taskState.items.find { it.id == childId }
        if (current == null) {
            Log.w("MainViewModel", "toggleSubtaskDone: task $childId not found")
            return
        }
        val updated = current.copy(status = if (done) TaskStatus.DONE else TaskStatus.OPEN)

        // Optimistic update
        _appState.update { currentState ->
            currentState.copy(
                taskState = TaskReducer.reduce(
                    currentState.taskState,
                    me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Update(updated)
                )
            )
        }

        // Server update with revert on failure
        viewModelScope.launch {
            val success = todoRepository.updateTaskOnServer(updated)
            if (!success) {
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = TaskReducer.reduce(
                            currentState.taskState,
                            me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Update(current)
                        )
                    )
                }
            }
        }
    }

    private fun handleManualAddSubmit(title: String, description: String?) {
        val currentState = _appState.value
        val dueAtInstant = currentState.dateTimePickerState.selectedDueDateMs?.let { timestamp ->
            Instant.fromEpochMilliseconds(timestamp)
        }
        
        val newTask = Task(
            id = System.currentTimeMillis(),
            title = title,
            content = description,
            createdAt = Clock.System.now(),
            dueAt = dueAtInstant,
            priority = currentState.priorityState.selectedPriority,
            status = TaskStatus.OPEN
        )
        
        // Add task
        _appState.update { state ->
            state.copy(
                taskState = TaskReducer.reduce(
                    state.taskState,
                    me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Add(newTask)
                )
            )
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
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = TaskReducer.reduce(
                            currentState.taskState,
                            me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Add(task)
                        )
                    )
                }
            }

            override suspend fun updateTask(task: Task) {
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = TaskReducer.reduce(
                            currentState.taskState,
                            me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Update(task)
                        )
                    )
                }
            }

            override suspend fun deleteTask(taskId: Long) {
                _appState.update { currentState ->
                    currentState.copy(
                        taskState = TaskReducer.reduce(
                            currentState.taskState,
                            me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Delete(taskId)
                        )
                    )
                }
            }

            override suspend fun addSubtask(parentId: Long, title: String): Task {
                todoRepository.addTask(title, parentId)
                // Return the newly created task (we need to find it in the repository)
                val children = todoRepository.getChildren(parentId)
                return children.maxByOrNull { it.createdAt } ?: throw IllegalStateException("Failed to create subtask")
            }

            override suspend fun getTask(taskId: Long): Task? {
                return _appState.value.taskState.items.find { it.id == taskId }
            }

            override suspend fun getChildren(parentId: Long): List<Task> {
                return todoRepository.getChildren(parentId)
            }
        }
    }

    // Utility methods for task relationships - delegate to repository
    fun getChildren(parentId: Long): List<Task> {
        return todoRepository.getChildren(parentId)
    }

    fun getParentProgress(parentId: Long): Pair<Int, Int> {
        return todoRepository.getParentProgress(parentId)
    }
}
