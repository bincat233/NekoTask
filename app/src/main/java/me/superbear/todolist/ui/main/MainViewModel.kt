package me.superbear.todolist.ui.main

import android.app.Application
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import me.superbear.todolist.ChatMessage
import me.superbear.todolist.Sender
import me.superbear.todolist.Task
import me.superbear.todolist.TodoRepository
import kotlin.random.Random

data class UiState(
    val items: List<Task>,
    val manualMode: Boolean,
    val manualTitle: String,
    val manualDesc: String,
    val messages: List<ChatMessage>,
    val fabWidthDp: Dp,
    val imeVisible: Boolean
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
}


class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val todoRepository = TodoRepository(application)

    private val _uiState = MutableStateFlow(UiState(
        items = emptyList(),
        manualMode = false,
        manualTitle = "",
        manualDesc = "",
        messages = emptyList(),
        fabWidthDp = 0.dp,
        imeVisible = false
    ))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            val tasks = todoRepository.getTasks("todolist_items.json")
            _uiState.update { it.copy(items = tasks) }
        }
    }

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.ToggleTask -> toggleTask(event.task)
            is UiEvent.OpenManual -> _uiState.update { it.copy(manualMode = true) }
            is UiEvent.CloseManual -> _uiState.update { it.copy(manualMode = false) }
            is UiEvent.ChangeTitle -> _uiState.update { it.copy(manualTitle = event.value) }
            is UiEvent.ChangeDesc -> _uiState.update { it.copy(manualDesc = event.value) }
            is UiEvent.ManualAddSubmit -> {
                val newTask = Task(
                    id = System.currentTimeMillis(),
                    title = event.title,
                    notes = event.description,
                    createdAtIso = Clock.System.now().toString(),
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
                    text = event.message,
                    sender = Sender.User,
                    timestamp = Clock.System.now()
                )
                _uiState.update {
                    it.copy(messages = it.messages + userMessage)
                }

                viewModelScope.launch {
                    delay(Random.nextLong(1000, 5000))
                    val assistantMessage = ChatMessage(
                        text = event.message,
                        sender = Sender.Assistant,
                        timestamp = Clock.System.now()
                    )
                    _uiState.update {
                        it.copy(messages = it.messages + assistantMessage)
                    }
                }
            }
            is UiEvent.FabMeasured -> _uiState.update { it.copy(fabWidthDp = event.widthDp) }
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
}