package me.superbear.todolist.assistant

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.datetime.Instant
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.domain.entities.Priority

@LLMDescription("Tools for managing the to-do list: add, update, delete, and complete tasks")
class TaskToolSet(
    private val repository: TodoRepository
) : ToolSet {

    @Tool
    @LLMDescription("Add a new task or subtask to the to-do list")
    fun add_task(
        @LLMDescription("Task title") title: String,
        @LLMDescription("Task notes or description") content: String? = null,
        @LLMDescription("ISO-8601 due date, e.g. 2026-06-23T15:00:00Z") dueAt: String? = null,
        @LLMDescription("Priority: LOW, MEDIUM, HIGH, or DEFAULT") priority: String? = null,
        @LLMDescription("Parent task ID for creating a subtask under it") parentId: Long? = null,
        @LLMDescription("Position order in parent's child list") orderInParent: Long? = null
    ): String {
        val p = mapPriority(priority)
        val due = parseInstant(dueAt)
        repository.addTask(title.trim(), parentId, content?.trim(), p, due)
        return "ok"
    }

    @Tool
    @LLMDescription("Update fields of an existing task by its ID")
    fun update_task(
        @LLMDescription("Task ID to update") id: Long,
        @LLMDescription("New title") title: String? = null,
        @LLMDescription("New notes or description") content: String? = null,
        @LLMDescription("ISO-8601 due date") dueAt: String? = null,
        @LLMDescription("New priority: LOW, MEDIUM, HIGH, or DEFAULT") priority: String? = null,
        @LLMDescription("New parent ID for moving the task to a different parent") parentId: Long? = null,
        @LLMDescription("New position in parent's child list") orderInParent: Long? = null
    ): String {
        val p = priority?.let { mapPriority(it) }
        val due = dueAt?.let { parseInstant(it) }
        if (parentId != null) {
            repository.moveTaskToParent(id, parentId, orderInParent)
        }
        repository.updateTask(id, title?.trim(), content?.trim(), p, due)
        return "ok"
    }

    @Tool
    @LLMDescription("Delete a task by its ID")
    fun delete_task(
        @LLMDescription("Task ID to delete") id: Long
    ): String {
        repository.deleteTask(id)
        return "ok"
    }

    @Tool
    @LLMDescription("Mark a task as completed by its ID")
    fun complete_task(
        @LLMDescription("Task ID to complete") id: Long
    ): String {
        repository.toggleTaskStatus(id, true)
        return "ok"
    }

    companion object {
        fun mapPriority(p: String?): Priority = when (p?.uppercase()) {
            "LOW" -> Priority.LOW
            "MEDIUM" -> Priority.MEDIUM
            "HIGH" -> Priority.HIGH
            else -> Priority.DEFAULT
        }

        fun parseInstant(iso: String?): Instant? = iso?.let {
            try {
                Instant.parse(it)
            } catch (_: Exception) {
                null
            }
        }
    }
}
