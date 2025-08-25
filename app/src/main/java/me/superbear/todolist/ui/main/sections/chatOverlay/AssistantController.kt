package me.superbear.todolist.ui.main.sections.chatOverlay

import android.util.Log
import kotlinx.datetime.Instant
import me.superbear.todolist.assistant.AssistantAction
import me.superbear.todolist.assistant.AssistantActionParser
import me.superbear.todolist.assistant.AssistantClient
import me.superbear.todolist.assistant.AssistantEnvelope
import me.superbear.todolist.domain.entities.ChatMessage
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus

/**
 * Controller that encapsulates assistant communication and action execution logic.
 * Separates assistant concerns from ViewModel and provides clean API for chat functionality.
 */
class AssistantController(
    private val mockAssistantClient: AssistantClient,
    private val realAssistantClient: AssistantClient,
    private val assistantActionParser: AssistantActionParser
) {
    
    /**
     * Send message to assistant and get response
     */
    suspend fun send(
        text: String, 
        currentMessages: List<ChatMessage>, 
        useMock: Boolean
    ): Result<AssistantEnvelope> {
        val client = if (useMock) mockAssistantClient else realAssistantClient
        
        return try {
            val response = client.send(text, currentMessages)
            response.fold(
                onSuccess = { responseText ->
                    val envelope = assistantActionParser.parseEnvelope(responseText).getOrElse {
                        AssistantEnvelope("(no text)", emptyList())
                    }
                    Result.success(envelope)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Execute assistant actions with provided hooks for task operations
     */
    suspend fun executeActions(
        actions: List<AssistantAction>,
        now: Instant,
        hooks: AssistantActionHooks
    ) {
        actions.forEach { action ->
            executeAction(action, now, hooks)
        }
        Log.d("AssistantController", "Executed ${actions.size} actions")
    }
    
    private suspend fun executeAction(
        action: AssistantAction,
        now: Instant,
        hooks: AssistantActionHooks
    ) {
        when (action) {
            is AssistantAction.AddTask -> {
                if (action.parentId != null) {
                    // Route to subtask creation
                    hooks.addSubtask(action.parentId, action.title)
                } else {
                    val newTask = Task(
                        id = System.currentTimeMillis(),
                        title = action.title,
                        content = action.content,
                        dueAt = parseToInstant(action.dueAtIso),
                        priority = mapPriority(action.priority),
                        status = TaskStatus.OPEN,
                        createdAt = now
                    )
                    hooks.addTask(newTask)
                }
            }
            is AssistantAction.DeleteTask -> {
                hooks.deleteTask(action.id)
            }
            is AssistantAction.UpdateTask -> {
                val currentTask = hooks.getTask(action.id)
                if (currentTask != null) {
                    var updatedTask = currentTask.copy(
                        title = action.title ?: currentTask.title,
                        content = action.content ?: currentTask.content,
                        dueAt = action.dueAtIso?.let { parseToInstant(it) } ?: currentTask.dueAt,
                        priority = action.priority?.let { mapPriority(it) } ?: currentTask.priority
                    )
                    // Reparent if a non-null parentId is provided
                    if (action.parentId != null && action.parentId != currentTask.parentId) {
                        val newParentId = action.parentId
                        val nextOrder = (newParentId?.let { hooks.getChildren(it).map { c -> c.orderInParent }.maxOrNull() } ?: -1L) + 1L
                        updatedTask = updatedTask.copy(parentId = newParentId, orderInParent = nextOrder)
                    }
                    hooks.updateTask(updatedTask)
                } else {
                    Log.w("AssistantController", "Could not find task with id ${action.id} to update")
                }
            }
            is AssistantAction.CompleteTask -> {
                val currentTask = hooks.getTask(action.id)
                if (currentTask != null) {
                    val completedTask = currentTask.copy(status = TaskStatus.DONE)
                    hooks.updateTask(completedTask)
                    Log.d("AssistantController", "Completed task with id ${action.id}")
                } else {
                    Log.w("AssistantController", "Could not find task with id ${action.id} to complete")
                }
            }
        }
    }
    
    private fun parseToInstant(isoString: String?): Instant? {
        return isoString?.let {
            try {
                Instant.parse(it)
            } catch (e: Exception) {
                Log.e("AssistantController", "Error parsing date: $it", e)
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

/**
 * Hooks interface for task operations that the AssistantController can call
 */
interface AssistantActionHooks {
    suspend fun addTask(task: Task)
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(taskId: Long)
    suspend fun addSubtask(parentId: Long, title: String): Task
    suspend fun getTask(taskId: Long): Task?
    suspend fun getChildren(parentId: Long): List<Task>
}
