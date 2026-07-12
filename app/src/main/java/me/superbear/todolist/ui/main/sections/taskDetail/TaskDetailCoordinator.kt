package me.superbear.todolist.ui.main.sections.taskDetail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.ui.main.sections.logFailure
import me.superbear.todolist.ui.main.sections.tasks.TaskListCoordinator

/**
 * Owns the task-detail sheet's state, debounced title/content persistence, and selected-task lookup.
 */
class TaskDetailCoordinator(
    private val todoRepository: TodoRepository,
    private val taskListCoordinator: TaskListCoordinator,
    private val viewModelScope: CoroutineScope
) {
    private val _state = MutableStateFlow(TaskDetailState())
    val state: StateFlow<TaskDetailState> = _state.asStateFlow()

    private var titleUpdateJob: Job? = null
    private var contentUpdateJob: Job? = null
    private val debounceDelayMs = 300L

    fun handleEvent(event: TaskDetailEvent) {
        when (event) {
            is TaskDetailEvent.ShowDetail -> {
                val task = taskListCoordinator.findTask(event.taskId)
                _state.update {
                    it.copy(
                        isVisible = true, selectedTaskId = event.taskId,
                        editedTitle = task?.title ?: "", editedContent = task?.content ?: ""
                    )
                }
            }
            is TaskDetailEvent.HideDetail -> {
                _state.update {
                    it.copy(isVisible = false, selectedTaskId = null, editedTitle = "", editedContent = "")
                }
            }
            is TaskDetailEvent.EditTitle -> {
                _state.update { it.copy(editedTitle = event.title) }
                debouncedTitleUpdate(event.title)
            }
            is TaskDetailEvent.EditContent -> {
                _state.update { it.copy(editedContent = event.content) }
                debouncedContentUpdate(event.content)
            }
            is TaskDetailEvent.UpdatePriority -> {
                viewModelScope.launch {
                    val updatedAt = Clock.System.now().toEpochMilliseconds()
                    todoRepository.updatePriority(event.taskId, event.priority, updatedAt)
                        .logFailure("TaskDetailCoordinator", "Failed to update task priority: ${event.taskId}")
                }
            }
            is TaskDetailEvent.DeleteTask -> {
                viewModelScope.launch {
                    val result = todoRepository.deleteTaskRecursively(event.taskId)
                    result.onSuccess {
                        _state.update {
                            it.copy(isVisible = false, selectedTaskId = null, editedTitle = "", editedContent = "")
                        }
                    }.logFailure("TaskDetailCoordinator", "Failed to delete task: ${event.taskId}")
                }
            }
        }
    }

    private fun debouncedTitleUpdate(newTitle: String) {
        val selectedTaskId = _state.value.selectedTaskId ?: return
        titleUpdateJob?.cancel()
        titleUpdateJob = viewModelScope.launch {
            delay(debounceDelayMs)
            if (_state.value.selectedTaskId == selectedTaskId) {
                val originalTask = taskListCoordinator.findTask(selectedTaskId)
                if (originalTask != null && originalTask.title != newTitle) {
                    val updatedAt = Clock.System.now().toEpochMilliseconds()
                    todoRepository.updateTitle(selectedTaskId, newTitle, updatedAt)
                        .logFailure("TaskDetailCoordinator", "Failed to update task title: $selectedTaskId")
                }
            }
        }
    }

    private fun debouncedContentUpdate(newContent: String) {
        val selectedTaskId = _state.value.selectedTaskId ?: return
        contentUpdateJob?.cancel()
        contentUpdateJob = viewModelScope.launch {
            delay(debounceDelayMs)
            if (_state.value.selectedTaskId == selectedTaskId) {
                val originalTask = taskListCoordinator.findTask(selectedTaskId)
                if (originalTask != null && originalTask.content != newContent) {
                    val updatedAt = Clock.System.now().toEpochMilliseconds()
                    todoRepository.updateContent(selectedTaskId, newContent.takeIf { it.isNotBlank() }, updatedAt)
                        .logFailure("TaskDetailCoordinator", "Failed to update task content: $selectedTaskId")
                }
            }
        }
    }

    fun getSelectedTask(): Task? {
        val selectedTaskId = _state.value.selectedTaskId ?: return null
        return taskListCoordinator.findTask(selectedTaskId)
    }

    fun setSubtaskDivisionLoading(loading: Boolean) {
        _state.update { it.copy(isSubtaskDivisionLoading = loading) }
    }
}
