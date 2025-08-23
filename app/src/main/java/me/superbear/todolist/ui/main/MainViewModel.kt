package me.superbear.todolist.ui.main

import android.app.Application
import android.util.Log
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.superbear.todolist.AssistantAction
import me.superbear.todolist.AssistantActionParser
import me.superbear.todolist.AssistantClient
import me.superbear.todolist.AssistantEnvelope
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.ChatMessage
import me.superbear.todolist.MessageStatus
import me.superbear.todolist.MockAssistantClient
import me.superbear.todolist.Priority
import me.superbear.todolist.RealAssistantClient
import me.superbear.todolist.Sender
import me.superbear.todolist.Task
import me.superbear.todolist.TaskStateSnapshotBuilder
import me.superbear.todolist.TodoRepository

// ⚠️ 提醒：
// 1. data class 主要是用来保存数据的“数据袋子”，编译器会自动生成
//    equals/hashCode/toString/copy/componentN 等方法
//    ——可以写方法，但最好只和数据本身相关，复杂逻辑应放到 ViewModel / Service。
// 2. 类名后面的小括号就是 **主构造函数** (primary constructor)。
//    - 写了 val/var 的参数会自动变成属性 (字段)，并生成 getter。
//    - 可以加默认值，比如 useMockAssistant: Boolean = true。
//    - 和 Java 里手写构造函数 + 字段声明等价。
data class UiState(
    val items: List<Task>,
    val manualMode: Boolean,
    val manualTitle: String,
    val manualDesc: String,
    val messages: List<ChatMessage>,
    val fabWidthDp: Dp,
    val imeVisible: Boolean,
    val useMockAssistant: Boolean = true,
    val executeAssistantActions: Boolean = true
)

// This is a sealed class that represents all possible UI events
// 做 “事件总线 / 消息类型枚举” 的事情，用来表达 UI 层可能发生的所有事件。
sealed class UiEvent {
    data class ToggleTask(val task: Task) : UiEvent()
    object OpenManual : UiEvent()
    object CloseManual : UiEvent()
    data class ChangeTitle(val value: String) : UiEvent()
    data class ChangeDesc(val value: String) : UiEvent()
    data class ManualAddSubmit(val title: String, val description: String?) : UiEvent()
    data class SendChat(val message: String) : UiEvent()
    data class FabMeasured(val widthDp: Dp) : UiEvent()
    data class SetUseMockAssistant(val useMock: Boolean) : UiEvent()
}


/**
 * MainViewModel — 数据与业务逻辑的“单一来源”，配合 Compose 实现单向数据流。
 *
 * 概念速记（面向 Java/C/JS 背景）：
 * - ViewModel：类似“带状态的控制器”，不持有 View 引用，专注管理 UI 需要的数据和业务。
 * - StateFlow：可订阅的“状态源”（像 JS 的 BehaviorSubject/Redux store），始终有一个当前值。
 * - MutableStateFlow：StateFlow 的可写版（VM 内部用它改值），对外暴露只读 StateFlow。
 * - 单向数据流：
 *   上行用回调（UI -> onEvent(event)），下行用状态流（VM -> uiState）。
 *
 * 读取建议：
 * 1) 看 onEvent(event)：UI 发上来的“回调入口”。
 * 2) 看各 handle* /set* /add* /update*：具体的状态变更方法。
 * 3) 看 init{}：实例创建后立即运行的初始化逻辑（类似 Java 构造体中的代码块）。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val todoRepository = TodoRepository(application)
    private val mockAssistantClient: AssistantClient = MockAssistantClient()
    private val realAssistantClient: AssistantClient = RealAssistantClient()
    private val assistantActionParser = AssistantActionParser()
    private val json = Json { prettyPrint = true }
    // _uiState 内部可变，如果是 uiState 就是对外只读
    // _uiState：MutableStateFlow，可写的“状态源”。仅 ViewModel 内部修改。
    private val _uiState = MutableStateFlow(UiState(
        items = emptyList(),
        manualMode = false,
        manualTitle = "",
        manualDesc = "",
        messages = emptyList(),
        fabWidthDp = 0.dp,
        imeVisible = false,
        useMockAssistant = BuildConfig.USE_MOCK_ASSISTANT
    ))
    // uiState：只读 StateFlow。UI 侧（Compose）通过 collectAsState() 订阅它来渲染。
    // 说明：“同一个流”——asStateFlow() 只是暴露只读视图，不复制数据源。
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    //fun setUseMockAssistant(useMock: Boolean) {
    //    onEvent(UiEvent.SetUseMockAssistant(useMock))
    //}

    // init{}：实例初始化块。每次创建本 ViewModel 实例时执行一次。
    // 用途：加载初始数据、建立与 AI 客户端的状态快照提供器等。
    init {
        loadTasks() // 启动时从仓库加载任务列表 -> 更新 _uiState
        (realAssistantClient as? RealAssistantClient)?.stateProvider = {
            // 将“当前任务快照”提供给 AI，便于模型理解现状并引用正确的 id
            TaskStateSnapshotBuilder.build(uiState.value.items)
        }
    }

    private fun loadTasks() {
        viewModelScope.launch {
            val tasks = todoRepository.getTasks("todolist_items.json")
            _uiState.update { it.copy(items = tasks) }
        }
    }


    // onEvent：UI 上传事件的“回调入口”。
    // View（Compose）里通过 viewModel::onEvent 把点击/输入等事件传上来。
    // 这里仅做路由分发，每个事件交由更小的私有方法处理（降低嵌套/提升可读性）。
    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.ToggleTask -> toggleTask(event.task)
            is UiEvent.OpenManual -> setManualMode(true)
            is UiEvent.CloseManual -> setManualMode(false)
            is UiEvent.ChangeTitle -> setManualTitle(event.value)
            is UiEvent.ChangeDesc -> setManualDesc(event.value)
            is UiEvent.ManualAddSubmit -> handleManualAddSubmit(event.title, event.description)
            is UiEvent.SendChat -> handleSendChat(event.message)
            is UiEvent.FabMeasured -> setFabWidth(event.widthDp)
            is UiEvent.SetUseMockAssistant -> setUseMockAssistant(event.useMock)
        }
    }

    // UI State Update Helpers
    private fun setManualMode(enabled: Boolean) {
        _uiState.update { it.copy(manualMode = enabled) }
    }

    private fun setManualTitle(title: String) {
        _uiState.update { it.copy(manualTitle = title) }
    }

    private fun setManualDesc(desc: String) {
        _uiState.update { it.copy(manualDesc = desc) }
    }

    private fun setFabWidth(widthDp: Dp) {
        _uiState.update { it.copy(fabWidthDp = widthDp) }
    }

    private fun setUseMockAssistant(useMock: Boolean) {
        _uiState.update { it.copy(useMockAssistant = useMock) }
    }

    // Task Management
    private fun handleManualAddSubmit(title: String, description: String?) {
        val newTask = Task(
            id = System.currentTimeMillis(),
            title = title,
            notes = description,
            createdAt = Clock.System.now(),
            status = "OPEN"
        )
        addTask(newTask)
        resetManualForm()
    }

    private fun addTask(task: Task) {
        _uiState.update {
            it.copy(items = listOf(task) + it.items)
        }
    }

    private fun deleteTask(taskId: Long) {
        val beforeCount = _uiState.value.items.size
        _uiState.update { state ->
            state.copy(items = state.items.filterNot { it.id == taskId })
        }
        val afterCount = _uiState.value.items.size
        if (beforeCount > afterCount) {
            Log.d("MainViewModel", "Deleted task with id $taskId")
        } else {
            Log.w("MainViewModel", "Could not find task with id $taskId to delete")
        }
    }

    private fun updateTask(updatedTask: Task) {
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.id == updatedTask.id) updatedTask else it })
        }
        Log.d("MainViewModel", "Updated task with id ${updatedTask.id}. New data: ${json.encodeToString(updatedTask)}")
    }

    private fun resetManualForm() {
        _uiState.update {
            it.copy(
                manualMode = false,
                manualTitle = "",
                manualDesc = ""
            )
        }
    }

    // Chat Message Management
    private fun addMessages(vararg messages: ChatMessage) {
        _uiState.update {
            it.copy(messages = it.messages + messages)
        }
    }

    private fun replaceMessage(oldMessageId: String, newMessage: ChatMessage) {
        _uiState.update { state ->
            val newMessages = state.messages.map { msg ->
                if (msg.id == oldMessageId) newMessage else msg
            }
            state.copy(messages = newMessages)
        }
    }

    private fun removeMessage(messageId: String) {
        _uiState.update { state ->
            state.copy(messages = state.messages.filter { it.id != messageId })
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
            status = MessageStatus.Sending
        )
        addMessages(userMessage, assistantMessage)

        viewModelScope.launch {
            sendToAssistant(message, assistantMessage)
        }
    }

    private suspend fun sendToAssistant(message: String, placeholderMessage: ChatMessage) {
        val assistantClient = if (uiState.value.useMockAssistant) {
            mockAssistantClient
        } else {
            realAssistantClient
        }
        
        val result = assistantClient.send(message, uiState.value.messages)
        result.onSuccess { assistantResponse ->
            handleAssistantResponse(assistantResponse, placeholderMessage)
        }.onFailure { error ->
            handleAssistantError(error, placeholderMessage)
        }
    }

    private fun handleAssistantResponse(response: String, placeholderMessage: ChatMessage) {
        val envelope = assistantActionParser.parseEnvelope(response).getOrElse {
            AssistantEnvelope("(no text)", emptyList())
        }

        // Handle assistant's text response
        if (!envelope.say.isNullOrBlank()) {
            val newAssistantMessage = placeholderMessage.copy(
                text = envelope.say,
                status = MessageStatus.Sent
            )
            replaceMessage(placeholderMessage.id, newAssistantMessage)
        } else {
            removeMessage(placeholderMessage.id)
        }

        // Handle assistant's actions
        if (envelope.actions.isNotEmpty()) {
            if (uiState.value.executeAssistantActions) {
                executeAssistantActions(envelope.actions)
            } else {
                Log.d("MainViewModel", "Parsed ${envelope.actions.size} actions: ${envelope.actions}")
            }
        }
    }

    private fun handleAssistantError(error: Throwable, placeholderMessage: ChatMessage) {
        Log.e("MainViewModel", "Error sending message", error)
        val errorMessage = placeholderMessage.copy(
            text = "[Error: unable to fetch response]",
            status = MessageStatus.Failed
        )
        replaceMessage(placeholderMessage.id, errorMessage)
    }

    private fun executeAssistantActions(actions: List<AssistantAction>) {
        actions.forEach { action ->
            executeAssistantAction(action)
        }
        Log.d("MainViewModel", "Executed ${actions.size} actions")
    }

    private fun executeAssistantAction(action: AssistantAction) {
        when (action) {
            is AssistantAction.AddTask -> {
                val newTask = Task(
                    id = System.currentTimeMillis(),
                    title = action.title,
                    notes = action.notes,
                    dueAt = parseToInstant(action.dueAtIso),
                    priority = mapPriority(action.priority),
                    status = "OPEN",
                    createdAt = Clock.System.now()
                )
                addTask(newTask)
            }
            is AssistantAction.DeleteTask -> {
                deleteTask(action.id)
            }
            is AssistantAction.UpdateTask -> {
                val taskToUpdate = _uiState.value.items.find { it.id == action.id }
                if (taskToUpdate != null) {
                    val updatedTask = taskToUpdate.copy(
                        title = action.title ?: taskToUpdate.title,
                        notes = action.notes ?: taskToUpdate.notes,
                        dueAt = action.dueAtIso?.let { parseToInstant(it) } ?: taskToUpdate.dueAt,
                        priority = action.priority?.let { mapPriority(it) } ?: taskToUpdate.priority
                    )
                    updateTask(updatedTask)
                } else {
                    Log.w("MainViewModel", "Could not find task with id ${action.id} to update")
                }
            }
            is AssistantAction.CompleteTask -> {
                val taskToComplete = _uiState.value.items.find { it.id == action.id }
                if (taskToComplete != null) {
                    val completedTask = taskToComplete.copy(status = "DONE")
                    updateTask(completedTask)
                    Log.d("MainViewModel", "Completed task with id ${action.id}")
                } else {
                    Log.w("MainViewModel", "Could not find task with id ${action.id} to complete")
                }
            }
        }
    }

    private fun toggleTask(task: Task) {
        val originalStatus = task.status
        val newStatus = if (originalStatus == "OPEN") "DONE" else "OPEN"
        val updatedTask = task.copy(status = newStatus)

        _uiState.update { currentState ->
            currentState.copy(
                items = currentState.items.map {
                    if (it.id == task.id) updatedTask else it
                }
            )
        }

        viewModelScope.launch {
            val success = todoRepository.updateTaskOnServer(updatedTask)
            if (!success) {
                _uiState.update { currentState ->
                    currentState.copy(
                        items = currentState.items.map {
                            if (it.id == task.id) task else it
                        }
                    )
                }
            }
        }
    }

    private fun parseToInstant(isoString: String?): Instant? {
        return isoString?.let {
            try {
                Instant.parse(it)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error parsing date: $it", e)
                null
            }
        }
    }

    private fun mapPriority(priority: String?): Priority {
        return when (priority?.uppercase()) {
            "LOW" -> Priority.LOW
            "MEDIUM" -> Priority.MEDIUM
            "HIGH" -> Priority.HIGH
            else -> Priority.DEFAULT
        }
    }
}
