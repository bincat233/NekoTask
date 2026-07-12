package me.superbear.todolist.assistant

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.datetime.Instant
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.domain.entities.Priority

@LLMDescription(
    "Tools for managing the to-do list: add, update, delete, complete tasks, and create checklist-style task trees"
)
class TaskToolSet(
    private val repository: TodoRepository
) : ToolSet {

    @Tool
    @LLMDescription(
        "Add one new task or one new subtask. For multi-item checklists, plans, packing lists, or preparation lists, use add_task_with_subtasks or add_subtasks instead."
    )
    suspend fun add_task(
        @LLMDescription("Task title") title: String,
        @LLMDescription("Short notes only. Do not put multi-item checklists here.") content: String? = null,
        @LLMDescription("ISO-8601 due date, e.g. 2026-06-23T15:00:00Z") dueAt: String? = null,
        @LLMDescription("Priority: LOW, MEDIUM, HIGH, or DEFAULT") priority: String? = null,
        @LLMDescription("Parent task ID for creating a subtask under it") parentId: Long? = null,
        @LLMDescription("Position order in parent's child list") orderInParent: Long? = null
    ): String {
        // Tool design guard: checklist items must stay independently checkable as tasks.
        // Returning a tool error gives the model a recoverable path to call the batch tools.
        if (looksLikeMultiItemChecklist(content)) {
            return "error: content looks like a multi-item checklist; use add_task_with_subtasks for a new parent task or add_subtasks for an existing parent task"
        }

        val p = mapPriority(priority)
        val due = parseInstant(dueAt)
        val result = if (orderInParent != null) {
            repository.insertTaskAt(title.trim(), parentId, orderInParent, content?.trim(), p, due)
        } else {
            repository.addTask(title.trim(), parentId, content?.trim(), p, due)
        }
        return result.fold(
            onSuccess = { id -> "ok: task_id=$id" },
            onFailure = { error -> error.toToolError() }
        )
    }

    @Tool
    @LLMDescription(
        "Create one parent task with ordered, checkable subtasks in one operation. Use for checklist, plan, packing list, preparation list, itinerary, or any request that asks for multiple actionable items."
    )
    suspend fun add_task_with_subtasks(
        @LLMDescription("Parent task title, e.g. Spring outing preparation checklist") title: String,
        @LLMDescription("Ordered subtask titles. If the user asks for N items, provide exactly N concise actionable titles.")
        subtaskTitles: List<String>,
        @LLMDescription("Optional short parent note. Do not duplicate the subtasks as numbered text here.") content: String? = null,
        @LLMDescription("ISO-8601 due date, e.g. 2026-06-23T15:00:00Z") dueAt: String? = null,
        @LLMDescription("Priority inherited by parent and subtasks: LOW, MEDIUM, HIGH, or DEFAULT") priority: String? = null
    ): String {
        val p = mapPriority(priority)
        val due = parseInstant(dueAt)
        return repository.addTaskWithSubtasks(
            title = title.trim(),
            subtaskTitles = subtaskTitles,
            content = content,
            priority = p,
            dueAt = due
        ).toTaskTreeToolResponse()
    }

    @Tool
    @LLMDescription(
        "Add multiple ordered, checkable subtasks under an existing parent task in one operation. Use when CURRENT_TODO_STATE already contains the parent task ID."
    )
    suspend fun add_subtasks(
        @LLMDescription("Existing parent task ID") parentId: Long,
        @LLMDescription("Ordered subtask titles. If the user asks for N items, provide exactly N concise actionable titles.")
        subtaskTitles: List<String>,
        @LLMDescription("Optional priority for new subtasks: LOW, MEDIUM, HIGH, or DEFAULT. Omit to inherit the parent task priority.")
        priority: String? = null,
        @LLMDescription("Optional ISO-8601 due date for new subtasks. Omit to inherit the parent task due date.") dueAt: String? = null
    ): String {
        val p = priority?.let { mapPriority(it) }
        val due = dueAt?.let { parseInstant(it) }
        return repository.addSubtasks(
            parentId = parentId,
            subtaskTitles = subtaskTitles,
            priority = p,
            dueAt = due
        ).toTaskTreeToolResponse()
    }

    @Tool
    @LLMDescription("Update fields of an existing task by its ID")
    suspend fun update_task(
        @LLMDescription("Task ID to update") id: Long,
        @LLMDescription("New title") title: String? = null,
        @LLMDescription("New notes or description") content: String? = null,
        @LLMDescription("ISO-8601 due date") dueAt: String? = null,
        @LLMDescription("New priority: LOW, MEDIUM, HIGH, or DEFAULT") priority: String? = null,
        @LLMDescription("New parent ID for moving the task to a different parent") parentId: Long? = null,
        @LLMDescription("New position in parent's child list") orderInParent: Long? = null
    ): String {
        if (looksLikeMultiItemChecklist(content)) {
            return "error: content looks like a multi-item checklist; keep task content short and use add_subtasks to create checkable child tasks"
        }

        val p = priority?.let { mapPriority(it) }
        val due = dueAt?.let { parseInstant(it) }
        if (parentId != null) {
            val moveResult = repository.moveTaskToParent(id, parentId, orderInParent)
            val moveResponse = moveResult.toBooleanToolResponse("ok", "error: task not found")
            if (!moveResponse.startsWith("ok")) return moveResponse
        } else if (orderInParent != null) {
            val reorderResult = repository.reorder(id, orderInParent)
            val reorderResponse = reorderResult.toBooleanToolResponse("ok", "error: task not found")
            if (!reorderResponse.startsWith("ok")) return reorderResponse
        }
        return repository.updateTask(id, title?.trim(), content?.trim(), p, due)
            .toBooleanToolResponse("ok", "error: task not found")
    }

    @Tool
    @LLMDescription("Delete a task by its ID")
    suspend fun delete_task(
        @LLMDescription("Task ID to delete") id: Long
    ): String {
        return repository.deleteTask(id).toBooleanToolResponse("ok", "error: task not found")
    }

    @Tool
    @LLMDescription("Mark a task as completed by its ID")
    suspend fun complete_task(
        @LLMDescription("Task ID to complete") id: Long
    ): String {
        return repository.toggleTaskStatus(id, true).toBooleanToolResponse("ok", "error: task not found")
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

        private fun Result<Boolean>.toBooleanToolResponse(
            successMessage: String,
            missingMessage: String
        ): String = fold(
            onSuccess = { success -> if (success) successMessage else missingMessage },
            onFailure = { error -> error.toToolError() }
        )

        private fun Result<me.superbear.todolist.data.TaskTreeInsertResult>.toTaskTreeToolResponse(): String = fold(
            onSuccess = { result ->
                "ok: parent_task_id=${result.parentId}, subtask_count=${result.subtaskIds.size}, subtask_ids=${result.subtaskIds}"
            },
            onFailure = { error -> error.toToolError() }
        )

        private fun looksLikeMultiItemChecklist(content: String?): Boolean {
            val lines = content?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toList()
                .orEmpty()
            if (lines.size < 3) return false

            val listLikeCount = lines.count { line ->
                line.matches(Regex("""^\d+[\.)、]\s+.+""")) ||
                    line.startsWith("- ") ||
                    line.startsWith("* ") ||
                    line.startsWith("• ")
            }
            return listLikeCount >= 3
        }

        private fun Throwable.toToolError(): String {
            return "error: ${message ?: this::class.simpleName ?: "unknown failure"}"
        }
    }
}
