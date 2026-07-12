package me.superbear.todolist.ui.main.sections.subtaskDivision

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.superbear.todolist.assistant.LlmRuntime
import me.superbear.todolist.assistant.subtask.DivisionStrategy
import me.superbear.todolist.assistant.subtask.SubtaskDivisionService
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.ui.main.sections.tasks.TaskListCoordinator

/**
 * Owns AI subtask-division orchestration (generate suggestions, create from suggestions, batch
 * divide). Builds a fresh [SubtaskDivisionService] per call from the shared [LlmRuntime]'s current
 * executor/model, so provider/model changes made via Settings take effect immediately.
 */
class SubtaskDivisionCoordinator(
    private val llmRuntime: LlmRuntime,
    private val todoRepository: TodoRepository,
    private val taskListCoordinator: TaskListCoordinator,
    private val viewModelScope: CoroutineScope
) {
    private val _state = MutableStateFlow(SubtaskDivisionState())
    val state: StateFlow<SubtaskDivisionState> = _state.asStateFlow()

    private fun getSubtaskDivisionService(): SubtaskDivisionService {
        return SubtaskDivisionService(
            promptExecutor = llmRuntime.buildExecutor(),
            model = llmRuntime.getCurrentModel(),
            todoRepository = todoRepository
        )
    }

    fun handleEvent(event: SubtaskDivisionEvent) {
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
        val task = taskListCoordinator.findTask(taskId)
        if (task == null) {
            Log.e("SubtaskDivisionCoordinator", "Task not found for subtask generation: $taskId")
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
                        Log.d("SubtaskDivisionCoordinator", "Generated ${response.subtasks.size} subtask suggestions")
                    },
                    onFailure = { error ->
                        Log.e("SubtaskDivisionCoordinator", "Failed to generate subtask suggestions", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("SubtaskDivisionCoordinator", "Unexpected error in subtask generation", e)
            }
        }
    }

    private fun createSubtasksFromAI(
        taskId: Long, strategy: DivisionStrategy?, maxSubtasks: Int?,
        context: String?, useAI: Boolean
    ) {
        val task = taskListCoordinator.findTask(taskId)
        if (task == null) {
            Log.e("SubtaskDivisionCoordinator", "Task not found for subtask creation: $taskId")
            return
        }

        viewModelScope.launch {
            try {
                _state.update { it.copy(isSubtaskDivisionLoading = true) }
                val result = getSubtaskDivisionService().divideAndCreateSubtasks(
                    parentTask = task, strategy = strategy, maxSubtasks = maxSubtasks,
                    context = context, useAI = useAI, forceCreate = true
                )
                result.fold(
                    onSuccess = { divisionResult ->
                        Log.d(
                            "SubtaskDivisionCoordinator",
                            "Successfully created ${divisionResult.createdTasks.size} subtasks"
                        )
                    },
                    onFailure = { error ->
                        Log.e("SubtaskDivisionCoordinator", "Failed to create subtasks", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("SubtaskDivisionCoordinator", "Unexpected error in subtask creation", e)
            } finally {
                _state.update { it.copy(isSubtaskDivisionLoading = false) }
            }
        }
    }

    private fun batchDivideSubtasks(
        taskIds: List<Long>, strategy: DivisionStrategy?, useAI: Boolean
    ) {
        val tasks = taskListCoordinator.taskState.value.items.filter { it.id in taskIds }
        if (tasks.isEmpty()) {
            Log.e("SubtaskDivisionCoordinator", "No valid tasks found for batch division")
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
                        Log.d("SubtaskDivisionCoordinator", "Batch division completed: $totalCreated subtasks created")
                    },
                    onFailure = { error -> Log.e("SubtaskDivisionCoordinator", "Batch division failed", error) }
                )
            } catch (e: Exception) {
                Log.e("SubtaskDivisionCoordinator", "Unexpected error in batch division", e)
            }
        }
    }
}
