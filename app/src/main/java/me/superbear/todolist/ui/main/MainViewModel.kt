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


class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val todoRepository = TodoRepository(application)
    private val mockAssistantClient: AssistantClient = MockAssistantClient()
    private val realAssistantClient: AssistantClient = RealAssistantClient()
    private val assistantActionParser = AssistantActionParser()

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
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun setUseMockAssistant(useMock: Boolean) {
        onEvent(UiEvent.SetUseMockAssistant(useMock))
    }
    
    init {
        loadTasks()
        (realAssistantClient as? RealAssistantClient)?.stateProvider = {
            TaskStateSnapshotBuilder.build(uiState.value.items)
        }
    }

    private fun loadTasks() {
        viewModelScope.launch {
            val tasks = todoRepository.getTasks("todolist_items.json")
            _uiState.update { it.copy(items = tasks) }
        }
    }

    fun onEvent(UiEvent: UiEvent) {
        when (UiEvent) {
            is UiEvent.ToggleTask -> toggleTask(UiEvent.task)
            is UiEvent.OpenManual -> _uiState.update { it.copy(manualMode = true) }
            is UiEvent.CloseManual -> _uiState.update { it.copy(manualMode = false) }
            is UiEvent.ChangeTitle -> _uiState.update { it.copy(manualTitle = UiEvent.value) }
            is UiEvent.ChangeDesc -> _uiState.update { it.copy(manualDesc = UiEvent.value) }
            is UiEvent.ManualAddSubmit -> {
                val newTask = Task(
                    id = System.currentTimeMillis(),
                    title = UiEvent.title,
                    notes = UiEvent.description,
                    createdAt = Clock.System.now(),
                    status = "OPEN"
                )
                _uiState.update {
                    it.copy(
                        items = listOf(newTask) + it.items,
                        manualMode = false,
                        manualTitle = "",
                        manualDesc = ""
                    )
                }
            }
            is UiEvent.SendChat -> {
                val userMessage = ChatMessage(
                    text = UiEvent.message,
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
                _uiState.update {
                    it.copy(messages = it.messages + userMessage + assistantMessage)
                }

                viewModelScope.launch {
                    val assistantClient = if (uiState.value.useMockAssistant) {
                        mockAssistantClient
                    } else {
                        realAssistantClient
                    }
                    val result = assistantClient.send(UiEvent.message, uiState.value.messages)
                    result.onSuccess { assistantResponse ->
                        val envelope = assistantActionParser.parseEnvelope(assistantResponse).getOrElse {
                            AssistantEnvelope("(no text)", emptyList())
                        }

                        if (!envelope.say.isNullOrBlank()) {
                            val newAssistantMessage = assistantMessage.copy(
                                text = envelope.say,
                                status = MessageStatus.Sent
                            )
                            _uiState.update { state ->
                                val newMessages = state.messages.map { msg ->
                                    if (msg.id == assistantMessage.id) newAssistantMessage else msg
                                 }
                                state.copy(messages = newMessages)
                            }
                        } else {
                            // If say is null, remove the placeholder message
                            _uiState.update { state ->
                                state.copy(messages = state.messages.filter { it.id != assistantMessage.id })
                            }
                        }

                        if (envelope.actions.isNotEmpty()) {
                            if (uiState.value.executeAssistantActions) {
                                envelope.actions.forEach { action ->
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
                                            _uiState.update {
                                                it.copy(items = listOf(newTask) + it.items)
                                            }
                                        }
                                        is AssistantAction.DeleteTask -> {
                                            val beforeCount = _uiState.value.items.size
                                            _uiState.update { state ->
                                                state.copy(items = state.items.filterNot { it.id == action.id })
                                            }
                                            val afterCount = _uiState.value.items.size
                                            if (beforeCount > afterCount) {
                                                Log.d("MainViewModel", "Deleted task with id ${action.id}")
                                            } else {
                                                Log.w("MainViewModel", "Could not find task with id ${action.id} to delete")
                                            }
                                        }
                                    }
                                }
                                Log.d("MainViewModel", "Executed ${envelope.actions.size} actions")
                            } else {
                                Log.d("MainViewModel", "Parsed ${envelope.actions.size} actions: ${envelope.actions}")
                            }
                        }
                    }.onFailure {
                        Log.e("MainViewModel", "Error sending message", it)
                        val finalAssistantMessage = assistantMessage.copy(
                            text = "[Error: unable to fetch response]",
                            status = MessageStatus.Failed
                        )
                        _uiState.update { state ->
                            val newMessages = state.messages.map { msg ->
                                if (msg.id == assistantMessage.id) finalAssistantMessage else msg
                            }
                            state.copy(messages = newMessages)
                        }
                    }
                }
            }
            is UiEvent.FabMeasured -> _uiState.update { it.copy(fabWidthDp = UiEvent.widthDp) }
            is UiEvent.SetUseMockAssistant -> _uiState.update {
                it.copy(useMockAssistant = UiEvent.useMock)
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
