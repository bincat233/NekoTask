package me.superbear.todolist.assistant

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.domain.entities.Priority

/** One item of a [TaskToolSet.add_tasks] batch call. Mirrors [TaskToolSet.add_task]'s scalar fields; no parentId/orderInParent — batch add is for independent top-level tasks only. */
@Serializable
data class TaskInput(
    val title: String,
    val content: String? = null,
    val dueAt: String? = null,
    val priority: String? = null
)

/** Full detail of a single task, returned by [TaskToolSet.get_task]. Includes content, which CURRENT_TODO_STATE omits. */
@Serializable
data class TaskDetail(
    val id: Long,
    val title: String,
    val content: String?,
    val status: String,
    val priority: String,
    val dueAt: String?,
    val parentId: Long?,
    val orderInParent: Long
)

@Serializable
data class BatchFailure(val index: Int, val error: String)

@Serializable
data class BatchResult(
    val successCount: Int,
    val failureCount: Int,
    val ids: List<Long>,
    val failures: List<BatchFailure>
)

@LLMDescription(
    "Tools for managing the to-do list: add, update, delete, complete tasks, and create checklist-style task trees"
)
class TaskToolSet(
    private val repository: TodoRepository
) : ToolSet {
    private val json = Json { encodeDefaults = true }

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
    @LLMDescription(
        "Add multiple independent, unrelated tasks in one operation (max $MAX_BATCH_SIZE). " +
            "For a checklist, plan, packing list, or preparation list under one parent, use add_task_with_subtasks instead. " +
            "Returns a JSON object {successCount, failureCount, ids, failures:[{index, error}]} — index is 1-based into the input list."
    )
    suspend fun add_tasks(
        @LLMDescription(
            "Independent top-level tasks to create, max $MAX_BATCH_SIZE. Do not use this for subtasks of an " +
                "existing or new parent — use add_subtasks or add_task_with_subtasks instead."
        )
        items: List<TaskInput>
    ): String {
        if (items.size > MAX_BATCH_SIZE) {
            return "error: max $MAX_BATCH_SIZE tasks per call, got ${items.size}"
        }

        val ids = mutableListOf<Long>()
        val failures = mutableListOf<BatchFailure>()
        items.forEachIndexed { index, item ->
            if (looksLikeMultiItemChecklist(item.content)) {
                failures += BatchFailure(
                    index + 1,
                    "content looks like a multi-item checklist; use add_task_with_subtasks or add_subtasks"
                )
                return@forEachIndexed
            }
            val p = mapPriority(item.priority)
            val due = parseInstant(item.dueAt)
            repository.addTask(item.title.trim(), null, item.content?.trim(), p, due).fold(
                onSuccess = { id -> ids += id },
                onFailure = { error -> failures += BatchFailure(index + 1, error.message ?: "unknown failure") }
            )
        }
        return json.encodeToString(BatchResult(ids.size, failures.size, ids, failures))
    }

    @Tool
    @LLMDescription("Update fields of an existing task by its ID")
    suspend fun update_task(
        @LLMDescription("Task ID to update") id: Long,
        @LLMDescription("New title") title: String? = null,
        @LLMDescription("New notes or description. Pass the literal string \"remove\" to clear existing content. Omit to leave unchanged.") content: String? = null,
        @LLMDescription("ISO-8601 due date. Pass the literal string \"remove\" to clear the existing due date. Omit to leave unchanged.") dueAt: String? = null,
        @LLMDescription("New priority: LOW, MEDIUM, HIGH, or DEFAULT") priority: String? = null,
        @LLMDescription("New parent ID for moving the task to a different parent") parentId: Long? = null,
        @LLMDescription("New position in parent's child list") orderInParent: Long? = null,
        @LLMDescription("Set true to detach this task from its parent and make it a top-level task. Ignored if parentId is also provided.") detachFromParent: Boolean = false
    ): String {
        val clearContent = content == REMOVE_SENTINEL
        val clearDueAt = dueAt == REMOVE_SENTINEL

        if (!clearContent && looksLikeMultiItemChecklist(content)) {
            return "error: content looks like a multi-item checklist; keep task content short and use add_subtasks to create checkable child tasks"
        }

        val p = priority?.let { mapPriority(it) }
        val due = if (clearDueAt) null else dueAt?.let { parseInstant(it) }

        if (parentId != null) {
            val success = repository.moveTaskToParent(id, parentId, orderInParent).getOrDefault(false)
            if (!success) return "error: task not found"
        } else if (detachFromParent) {
            val success = repository.moveTaskToParent(id, null, orderInParent).getOrDefault(false)
            if (!success) return "error: task not found"
        } else if (orderInParent != null) {
            val success = repository.reorder(id, orderInParent).getOrDefault(false)
            if (!success) return "error: task not found"
        }

        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (clearContent) repository.updateContent(id, null, nowMs)
        if (clearDueAt) repository.updateDueAt(id, null, nowMs)

        val remainingContent = if (clearContent) null else content?.trim()
        return repository.updateTask(id, title?.trim(), remainingContent, p, due)
            .toBooleanToolResponse("ok", "error: task not found")
    }

    @Tool
    @LLMDescription("Delete a task by its ID. If it has subtasks, they are deleted too (recursive).")
    suspend fun delete_task(
        @LLMDescription("Task ID to delete") id: Long
    ): String {
        return repository.deleteTaskRecursively(id).fold(
            onSuccess = { count ->
                when {
                    count <= 0 -> "error: task not found"
                    count == 1 -> "ok: deleted task"
                    else -> "ok: deleted task and ${count - 1} subtask(s)"
                }
            },
            onFailure = { it.toToolError() }
        )
    }

    @Tool
    @LLMDescription(
        "Fetch full details of a task by ID, including its content/notes which are NOT included in " +
            "CURRENT_TODO_STATE. Call this before appending to or referencing a task's existing notes. " +
            "Returns a JSON object."
    )
    suspend fun get_task(
        @LLMDescription("Task ID to fetch") id: Long
    ): String {
        val task = repository.getTaskById(id) ?: return "error: task not found"
        return json.encodeToString(
            TaskDetail(
                id = task.id ?: -1L,
                title = task.title,
                content = task.content,
                status = task.status.name,
                priority = task.priority.name,
                dueAt = task.dueAt?.toString(),
                parentId = task.parentId,
                orderInParent = task.orderInParent
            )
        )
    }

    @Tool
    @LLMDescription("Mark a task as completed by its ID")
    suspend fun complete_task(
        @LLMDescription("Task ID to complete") id: Long
    ): String {
        return repository.toggleTaskStatus(id, true).toBooleanToolResponse("ok", "error: task not found")
    }

    @Tool
    @LLMDescription("Mark a completed task as not completed (reopen it) by its ID")
    suspend fun uncomplete_task(
        @LLMDescription("Task ID to reopen") id: Long
    ): String {
        return repository.toggleTaskStatus(id, false).toBooleanToolResponse("ok", "error: task not found")
    }

    @Tool
    @LLMDescription(
        "Mark multiple tasks as completed in one operation (max $MAX_BATCH_SIZE). " +
            "Returns a JSON object {successCount, failureCount, ids, failures:[{index, error}]} — index is 1-based into the input list."
    )
    suspend fun complete_tasks(
        @LLMDescription("Task IDs to complete, max $MAX_BATCH_SIZE") ids: List<Long>
    ): String {
        if (ids.size > MAX_BATCH_SIZE) {
            return "error: max $MAX_BATCH_SIZE tasks per call, got ${ids.size}"
        }

        val completed = mutableListOf<Long>()
        val failures = mutableListOf<BatchFailure>()
        ids.forEachIndexed { index, id ->
            repository.toggleTaskStatus(id, true).fold(
                onSuccess = { success ->
                    if (success) completed += id else failures += BatchFailure(index + 1, "task not found")
                },
                onFailure = { error -> failures += BatchFailure(index + 1, error.message ?: "unknown failure") }
            )
        }
        return json.encodeToString(BatchResult(completed.size, failures.size, completed, failures))
    }

    companion object {
        const val MAX_BATCH_SIZE = 25
        const val REMOVE_SENTINEL = "remove"

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
