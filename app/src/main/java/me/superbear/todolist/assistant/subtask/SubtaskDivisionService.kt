package me.superbear.todolist.assistant.subtask

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import android.util.Log
import kotlinx.datetime.Clock
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus

/**
 * Subtask Division Service
 * Integrates AI divider and database operations to provide complete subtask division functionality
 */
class SubtaskDivisionService(
    private val promptExecutor: PromptExecutor,
    private val model: LLModel,
    private val todoRepository: TodoRepository
) {
    private val aiDivider = AISubtaskDivider(promptExecutor, model)
    private val mockDivider = MockSubtaskDivider()

    /**
     * Generate subtask suggestions for specified task
     */
    suspend fun generateSubtaskSuggestions(
        task: Task,
        strategy: DivisionStrategy? = null,
        maxSubtasks: Int? = null,
        context: String? = null,
        useAI: Boolean = true
    ): Result<SubtaskDivisionResponse> {
        val config = SubtaskDivisionConfigManager.getConfig()

        val request = SubtaskDivisionRequest(
            taskTitle = task.title,
            taskContent = task.content,
            taskPriority = if (config.enablePriorityInheritance) task.priority else Priority.DEFAULT,
            maxSubtasks = maxSubtasks ?: config.defaultMaxSubtasks,
            strategy = strategy ?: config.defaultStrategy,
            context = context
        )

        return if (useAI) {
            aiDivider.divideTask(request)
        } else {
            mockDivider.divideTask(request)
        }
    }

    /**
     * Generate subtask suggestions and create directly to database (if configuration allows)
     */
    suspend fun divideAndCreateSubtasks(
        parentTask: Task,
        strategy: DivisionStrategy? = null,
        maxSubtasks: Int? = null,
        context: String? = null,
        useAI: Boolean = true,
        forceCreate: Boolean = false
    ): Result<SubtaskDivisionResult> {
        val config = SubtaskDivisionConfigManager.getConfig()

        val suggestionResult = generateSubtaskSuggestions(
            task = parentTask,
            strategy = strategy,
            maxSubtasks = maxSubtasks,
            context = context,
            useAI = useAI
        )

        return suggestionResult.fold(
            onSuccess = { response ->
                if (config.autoCreateSubtasks || forceCreate) {
                    createSubtasksFromSuggestions(parentTask, response)
                } else {
                    Result.success(
                        SubtaskDivisionResult(
                            suggestions = response,
                            createdTasks = emptyList(),
                            wasAutoCreated = false
                        )
                    )
                }
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    /**
     * Create actual subtasks based on suggestions
     */
    suspend fun createSubtasksFromSuggestions(
        parentTask: Task,
        suggestions: SubtaskDivisionResponse
    ): Result<SubtaskDivisionResult> {
        return try {
            val now = Clock.System.now()
            val createdTasks = mutableListOf<Task>()

            suggestions.subtasks.sortedBy { it.estimatedOrder }.forEach { suggestion ->
                todoRepository.addTask(
                    title = suggestion.title,
                    parentId = parentTask.id,
                    content = suggestion.content,
                    priority = suggestion.priority,
                    dueAt = null,
                    status = TaskStatus.OPEN
                )

                val tempTask = Task(
                    id = null,
                    title = suggestion.title,
                    content = suggestion.content,
                    status = TaskStatus.OPEN,
                    priority = suggestion.priority,
                    parentId = parentTask.id,
                    orderInParent = suggestion.estimatedOrder.toLong(),
                    createdAt = now,
                    updatedAt = now,
                    dueAt = null
                )
                createdTasks.add(tempTask)

                Log.d("SubtaskDivisionService", "Created subtask: ${suggestion.title}")
            }

            Result.success(
                SubtaskDivisionResult(
                    suggestions = suggestions,
                    createdTasks = createdTasks,
                    wasAutoCreated = true
                )
            )
        } catch (e: Exception) {
            Log.e("SubtaskDivisionService", "Failed to create subtasks", e)
            Result.failure(Exception("Failed to create subtasks: ${e.message}"))
        }
    }

    /**
     * Batch create subtasks for multiple tasks
     */
    suspend fun batchDivideAndCreate(
        tasks: List<Task>,
        strategy: DivisionStrategy? = null,
        useAI: Boolean = true
    ): Result<List<SubtaskDivisionResult>> {
        val results = mutableListOf<SubtaskDivisionResult>()

        for (task in tasks) {
            val result = divideAndCreateSubtasks(
                parentTask = task,
                strategy = strategy,
                useAI = useAI,
                forceCreate = true
            )

            result.fold(
                onSuccess = { divisionResult ->
                    results.add(divisionResult)
                },
                onFailure = { error ->
                    Log.e("SubtaskDivisionService", "Failed to divide task: ${task.title}", error)
                }
            )
        }

        return Result.success(results)
    }
}

data class SubtaskDivisionResult(
    val suggestions: SubtaskDivisionResponse,
    val createdTasks: List<Task>,
    val wasAutoCreated: Boolean
)
