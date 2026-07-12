package me.superbear.todolist.ui.main.sections.tasks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.superbear.todolist.assistant.CurrentTodoStateSnapshotBuilder
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus
import me.superbear.todolist.ui.main.sections.logFailure

/**
 * Owns task-list CRUD orchestration, the current task list, task lookups,
 * the AI-context snapshot builder, and the debug-only sample-data reset.
 */
class TaskListCoordinator(
    private val todoRepository: TodoRepository,
    private val viewModelScope: CoroutineScope,
    private val snapshotBuilder: CurrentTodoStateSnapshotBuilder = CurrentTodoStateSnapshotBuilder()
) {
    private val _taskState = MutableStateFlow(TaskState())
    val taskState: StateFlow<TaskState> = _taskState.asStateFlow()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            _taskState.update { it.copy(isLoading = true) }
            todoRepository.tasks.collect { tasks ->
                _taskState.update { it.copy(items = tasks, isLoading = false) }
            }
        }
    }

    fun handleEvent(event: TaskEvent) {
        when (event) {
            is TaskEvent.Toggle -> toggleTaskPersist(event.task)
            is TaskEvent.Add -> {
                viewModelScope.launch {
                    val t = event.task
                    todoRepository.addTask(
                        title = t.title, parentId = t.parentId, content = t.content,
                        priority = t.priority, dueAt = t.dueAt, status = t.status
                    ).logFailure("TaskListCoordinator", "Failed to add task: ${t.title}")
                }
            }
            is TaskEvent.Update -> {
                val t = event.task
                val id = t.id
                if (id != null) {
                    viewModelScope.launch {
                        todoRepository.updateTask(
                            id = id, title = t.title, content = t.content,
                            priority = t.priority, dueAt = t.dueAt
                        ).logFailure("TaskListCoordinator", "Failed to update task: $id")
                    }
                }
            }
            is TaskEvent.Delete -> {
                viewModelScope.launch {
                    todoRepository.deleteTask(event.taskId)
                        .logFailure("TaskListCoordinator", "Failed to delete task: ${event.taskId}")
                }
            }
            is TaskEvent.AddSubtask -> {
                viewModelScope.launch {
                    if (event.order != null) {
                        todoRepository.insertTaskAt(title = event.title, parentId = event.parentId, order = event.order)
                    } else {
                        todoRepository.addTask(title = event.title, parentId = event.parentId)
                    }.logFailure("TaskListCoordinator", "Failed to add subtask under ${event.parentId}: ${event.title}")
                }
            }
            is TaskEvent.DeleteSubtask -> {
                viewModelScope.launch {
                    todoRepository.deleteTask(event.subtaskId)
                        .logFailure("TaskListCoordinator", "Failed to delete subtask: ${event.subtaskId}")
                }
            }
            is TaskEvent.ToggleSubtask -> {
                viewModelScope.launch {
                    todoRepository.toggleTaskStatus(event.childId, event.done)
                        .logFailure("TaskListCoordinator", "Failed to toggle subtask: ${event.childId}")
                }
            }
            is TaskEvent.UpdateSubtaskTitle -> {
                viewModelScope.launch {
                    todoRepository.updateTask(id = event.subtaskId, title = event.newTitle)
                        .logFailure("TaskListCoordinator", "Failed to update subtask title: ${event.subtaskId}")
                }
            }
            is TaskEvent.LoadTasks -> { loadTasks() }
            is TaskEvent.TasksLoaded -> {
                _taskState.update { it.copy(items = event.tasks, isLoading = false) }
            }
        }
    }

    private fun toggleTaskPersist(task: Task) {
        val newDone = task.status == TaskStatus.OPEN
        val id = task.id ?: return
        viewModelScope.launch {
            todoRepository.toggleTaskStatus(id, newDone).logFailure("TaskListCoordinator", "Failed to toggle task: $id")
        }
    }

    fun getChildren(parentId: Long): List<Task> = _taskState.value.items.filter { it.parentId == parentId }

    fun getParentProgress(parentId: Long): Pair<Int, Int> {
        val children = getChildren(parentId)
        val doneCount = children.count { it.status == TaskStatus.DONE }
        return doneCount to children.size
    }

    fun findTask(id: Long): Task? = _taskState.value.items.find { it.id == id }

    /**
     * Debug-only developer setting: wipes all tasks and re-seeds sample data.
     * No-op in release builds (see [TodoRepository.resetSampleData]).
     */
    fun resetSampleData() {
        viewModelScope.launch {
            todoRepository.resetSampleData()
        }
    }

    fun buildCurrentTodoStateSnapshot(): String {
        return snapshotBuilder.build(_taskState.value.items)
    }
}
