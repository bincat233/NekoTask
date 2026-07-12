package me.superbear.todolist.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.data.model.AppDatabase
import me.superbear.todolist.data.model.toDomain
import me.superbear.todolist.data.model.toEntity
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus

/**
 * Repository for task data operations.
 *
 * Room is the source of truth. Write operations are suspend functions with explicit
 * [Result] values so callers can wait for success/failure instead of relying on
 * fire-and-forget repository jobs.
 */
class TodoRepository(
    internal val database: AppDatabase,
    private val seedManager: SeedManager? = null
) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tasks: Flow<List<Task>> = database.taskDao().observeAll().map { entities ->
        entities.toDomain()
    }
        // IMPORTANT: Delete database before lazy initialization if this is ever re-enabled.
    init {
        initializeDatabase()
    }

    private suspend fun <T> dbWrite(description: String, block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TodoRepository", "Failed to $description", e)
                Result.failure(e)
            }
        }

    private fun initializeDatabase() {
        val seeder = seedManager ?: return
        repositoryScope.launch {
            try {
                if (seeder.needsSeeding(database)) {
                    Log.d("TodoRepository", "Database empty, seeding with built-in sample data.")
                    seeder.seedWithSampleData(database)
                    Log.d("TodoRepository", "Database sample seeding completed successfully.")
                } else {
                    Log.d("TodoRepository", "Database already seeded, skipping.")
                }
            } catch (e: Exception) {
                Log.e("TodoRepository", "Failed to initialize database", e)
            }
        }
    }

    suspend fun resetSampleData() {
        val seeder = seedManager ?: return
        seeder.resetAndReseed(database)
    }

    suspend fun getTaskById(id: Long): Task? {
        return try {
            withContext(Dispatchers.IO) {
                database.taskDao().getTaskById(id)?.toDomain()
            }
        } catch (e: Exception) {
            Log.e("TodoRepository", "Failed to get task by id: $id", e)
            null
        }
    }

    suspend fun addTask(
        title: String,
        parentId: Long? = null,
        content: String? = null,
        priority: Priority = Priority.DEFAULT,
        dueAt: Instant? = null,
        status: TaskStatus = TaskStatus.OPEN
    ): Result<Long> = dbWrite("add task: $title") {
        val now = Clock.System.now()
        val newTask = Task(
            id = null,
            title = title,
            content = content,
            status = status,
            priority = priority,
            parentId = parentId,
            dueAt = dueAt,
            createdAt = now,
            updatedAt = now,
            orderInParent = 0
        )

        val rowId = database.taskDao().addTaskWithOrdering(newTask.toEntity())
        Log.d("TodoRepository", "Added new task: $title (rowId: $rowId)")
        rowId
    }

    /**
     * Atomic AI-tool operation for checklist-like requests.
     *
     * A request such as "make a 20 item packing list" should produce one parent task with
     * checkable child tasks, not a numbered blob in [Task.content]. Keeping this as a
     * repository primitive avoids partial task trees if any child insert fails.
     */
    suspend fun addTaskWithSubtasks(
        title: String,
        subtaskTitles: List<String>,
        content: String? = null,
        priority: Priority = Priority.DEFAULT,
        dueAt: Instant? = null
    ): Result<TaskTreeInsertResult> = dbWrite("add task with ${subtaskTitles.size} subtasks: $title") {
        require(title.isNotBlank()) { "Parent task title must not be blank" }

        val cleanedSubtaskTitles = cleanSubtaskTitles(subtaskTitles)
        require(cleanedSubtaskTitles.isNotEmpty()) { "At least one subtask title is required" }

        database.withTransaction {
            val now = Clock.System.now()
            val parentOrder = (database.taskDao().getMaxOrderInParent(parentId = null) ?: -1L) + 1L
            val parentTask = Task(
                id = null,
                title = title.trim(),
                content = content?.trim()?.takeIf { it.isNotBlank() },
                status = TaskStatus.OPEN,
                priority = priority,
                parentId = null,
                dueAt = dueAt,
                createdAt = now,
                updatedAt = now,
                orderInParent = parentOrder
            )

            val parentId = database.taskDao().insert(parentTask.toEntity())
            val subtaskIds = insertSubtasks(parentId, cleanedSubtaskTitles, priority, dueAt, now)

            Log.d(
                "TodoRepository",
                "Added task tree: $title (parentId: $parentId, subtasks: ${subtaskIds.size})"
            )
            TaskTreeInsertResult(parentId = parentId, subtaskIds = subtaskIds)
        }
    }

    /**
     * Atomic AI-tool operation for adding many checkable children to an existing task.
     * This prevents the model from burning one tool-call iteration per item.
     */
    suspend fun addSubtasks(
        parentId: Long,
        subtaskTitles: List<String>,
        priority: Priority? = null,
        dueAt: Instant? = null
    ): Result<TaskTreeInsertResult> = dbWrite("add ${subtaskTitles.size} subtasks under $parentId") {
        val cleanedSubtaskTitles = cleanSubtaskTitles(subtaskTitles)
        require(cleanedSubtaskTitles.isNotEmpty()) { "At least one subtask title is required" }

        database.withTransaction {
            val parent = database.taskDao().getTaskById(parentId)?.toDomain()
                ?: throw IllegalArgumentException("Parent task not found: $parentId")
            val now = Clock.System.now()
            val subtaskIds = insertSubtasks(
                parentId = parentId,
                subtaskTitles = cleanedSubtaskTitles,
                priority = priority ?: parent.priority,
                dueAt = dueAt ?: parent.dueAt,
                now = now
            )

            Log.d("TodoRepository", "Added ${subtaskIds.size} subtasks under parent=$parentId")
            TaskTreeInsertResult(parentId = parentId, subtaskIds = subtaskIds)
        }
    }

    suspend fun addDetailedSubtasks(
        parentId: Long,
        subtasks: List<SubtaskDraft>
    ): Result<List<Task>> = dbWrite("add ${subtasks.size} detailed subtasks under $parentId") {
        require(subtasks.isNotEmpty()) { "At least one subtask is required" }

        database.withTransaction {
            val parent = database.taskDao().getTaskById(parentId)?.toDomain()
                ?: throw IllegalArgumentException("Parent task not found: $parentId")
            val now = Clock.System.now()
            
            val firstOrder = (database.taskDao().getMaxOrderInParent(parentId) ?: -1L) + 1L
            val sortedSubtasks = subtasks.sortedBy { it.estimatedOrder ?: 0 }
            
            val createdTasks = sortedSubtasks.mapIndexed { index, draft ->
                val subtask = Task(
                    id = null,
                    title = draft.title,
                    content = draft.content,
                    status = TaskStatus.OPEN,
                    priority = draft.priority,
                    parentId = parentId,
                    dueAt = draft.dueAt,
                    createdAt = now,
                    updatedAt = now,
                    orderInParent = firstOrder + index.toLong()
                )
                val rowId = database.taskDao().insert(subtask.toEntity())
                subtask.copy(id = rowId)
            }

            Log.d("TodoRepository", "Added ${createdTasks.size} detailed subtasks under parent=$parentId")
            createdTasks
        }
    }

    suspend fun insertTaskAt(
        title: String,
        parentId: Long? = null,
        order: Long,
        content: String? = null,
        priority: Priority = Priority.DEFAULT,
        dueAt: Instant? = null,
        status: TaskStatus = TaskStatus.OPEN
    ): Result<Long> = dbWrite("insert task at position $order: $title") {
        val now = Clock.System.now()
        database.taskDao().bulkShiftOrders(parentId, order, 1L, now.toEpochMilliseconds())

        val newTask = Task(
            id = null,
            title = title,
            content = content,
            status = status,
            priority = priority,
            parentId = parentId,
            dueAt = dueAt,
            createdAt = now,
            updatedAt = now,
            orderInParent = order
        )

        val rowId = database.taskDao().insert(newTask.toEntity())
        Log.d("TodoRepository", "Inserted task at position $order: $title (rowId: $rowId)")
        rowId
    }

    suspend fun addTaskBefore(
        targetId: Long,
        title: String,
        content: String? = null,
        priority: Priority = Priority.DEFAULT
    ): Result<Long> = dbWrite("add task before $targetId: $title") {
        val targetTask = database.taskDao().getTaskById(targetId)
            ?: throw IllegalArgumentException("Target task not found: $targetId")

        val now = Clock.System.now()
        val shiftedRows = database.taskDao().bulkShiftOrders(
            parentId = targetTask.parentId,
            fromInclusive = targetTask.orderInParent,
            delta = 1L,
            updatedAt = now.toEpochMilliseconds()
        )
        val newTask = Task(
            id = null,
            title = title,
            content = content,
            status = TaskStatus.OPEN,
            priority = priority,
            parentId = targetTask.parentId,
            createdAt = now,
            updatedAt = now,
            orderInParent = targetTask.orderInParent
        )

        val rowId = database.taskDao().insert(newTask.toEntity())
        Log.d("TodoRepository", "Added task before $targetId: $title (shifted $shiftedRows tasks, rowId: $rowId)")
        rowId
    }

    suspend fun updateTask(
        id: Long,
        title: String? = null,
        content: String? = null,
        priority: Priority? = null,
        dueAt: Instant? = null
    ): Result<Boolean> = dbWrite("update task: $id") {
        val currentTask = database.taskDao().getTaskById(id)?.toDomain()
        if (currentTask == null) {
            Log.w("TodoRepository", "Task not found for update: $id")
            return@dbWrite false
        }

        val updatedTask = currentTask.copy(
            title = title ?: currentTask.title,
            content = content ?: currentTask.content,
            priority = priority ?: currentTask.priority,
            dueAt = dueAt ?: currentTask.dueAt,
            updatedAt = Clock.System.now()
        )

        val rowsUpdated = database.taskDao().update(updatedTask.toEntity())
        if (rowsUpdated > 0) {
            Log.d("TodoRepository", "Updated task: $id")
            true
        } else {
            Log.w("TodoRepository", "Task not found for update: $id")
            false
        }
    }

    suspend fun updateTitle(id: Long, title: String, updatedAt: Long): Result<Boolean> =
        dbWrite("update task title: $id") {
            val rowsUpdated = database.taskDao().updateTitle(id, title, updatedAt)
            logRowsUpdated("Updated task title: $id -> $title", "Task not found for title update: $id", rowsUpdated)
        }

    suspend fun updateContent(id: Long, content: String?, updatedAt: Long): Result<Boolean> =
        dbWrite("update task content: $id") {
            val rowsUpdated = database.taskDao().updateContent(id, content, updatedAt)
            logRowsUpdated("Updated task content: $id", "Task not found for content update: $id", rowsUpdated)
        }

    suspend fun updateStatus(id: Long, status: TaskStatus, updatedAt: Long): Result<Boolean> =
        dbWrite("update task status: $id") {
            val rowsUpdated = database.withTransaction {
                updateStatusWithDoneCascade(id, status, updatedAt)
            }
            logRowsUpdated("Updated task status: $id -> $status", "Task not found for status update: $id", rowsUpdated)
        }

    suspend fun updateDueAt(id: Long, dueAt: Instant?, updatedAt: Long): Result<Boolean> =
        dbWrite("update task due date: $id") {
            val dueAtMs = dueAt?.toEpochMilliseconds()
            val rowsUpdated = database.taskDao().updateDueAt(id, dueAtMs, updatedAt)
            logRowsUpdated("Updated task due date: $id -> $dueAt", "Task not found for due date update: $id", rowsUpdated)
        }

    suspend fun updatePriority(id: Long, priority: Priority, updatedAt: Long): Result<Boolean> =
        dbWrite("update task priority: $id") {
            val rowsUpdated = database.taskDao().updatePriority(id, priority, updatedAt)
            logRowsUpdated("Updated task priority: $id -> $priority", "Task not found for priority update: $id", rowsUpdated)
        }

    suspend fun toggleTaskStatus(id: Long, done: Boolean): Result<Boolean> =
        dbWrite("toggle task status: $id") {
            val newStatus = if (done) TaskStatus.DONE else TaskStatus.OPEN
            val updatedAt = Clock.System.now().toEpochMilliseconds()
            val rowsUpdated = database.withTransaction {
                updateStatusWithDoneCascade(id, newStatus, updatedAt)
            }
            logRowsUpdated("Toggled task status: $id -> $done", "Task not found for toggle: $id", rowsUpdated)
        }

    suspend fun deleteTask(id: Long): Result<Boolean> = dbWrite("delete task: $id") {
        val task = database.taskDao().getTaskById(id)
        val parentId = task?.parentId

        val rowsDeleted = database.taskDao().deleteById(id)
        if (rowsDeleted > 0) {
            database.taskOrderOpsDao().reindexParent(parentId, Clock.System.now().toEpochMilliseconds())
            Log.d("TodoRepository", "Deleted task: $id and reindexed siblings of parent=$parentId")
            true
        } else {
            Log.w("TodoRepository", "Task not found for deletion: $id")
            false
        }
    }

    suspend fun deleteTaskRecursively(id: Long): Result<Int> = dbWrite("recursively delete task: $id") {
        val task = database.taskDao().getTaskById(id)
        val parentId = task?.parentId
        val totalDeleted = database.taskDao().deleteTaskRecursively(id)

        if (totalDeleted > 0) {
            database.taskOrderOpsDao().reindexParent(parentId, Clock.System.now().toEpochMilliseconds())
            Log.d("TodoRepository", "Recursively deleted $totalDeleted tasks starting from: $id and reindexed siblings of parent=$parentId")
        } else {
            Log.w("TodoRepository", "Task not found for recursive deletion: $id")
        }
        totalDeleted
    }

    suspend fun reorder(taskId: Long, newOrder: Long): Result<Boolean> =
        dbWrite("reorder task $taskId to $newOrder") {
            val task = database.taskDao().getTaskById(taskId)
            if (task == null) {
                Log.w("TodoRepository", "Task not found for reorder: $taskId")
                return@dbWrite false
            }

            database.taskDao().reorderWithinParent(task.parentId, taskId, newOrder, Clock.System.now().toEpochMilliseconds())
            Log.d("TodoRepository", "Reordered task $taskId within parent ${task.parentId} to $newOrder")
            true
        }

    suspend fun moveTaskToParent(taskId: Long, newParentId: Long?, newIndex: Long? = null): Result<Boolean> =
        dbWrite("move task $taskId to parent=$newParentId at index=$newIndex") {
            val task = database.taskOrderOpsDao().getTaskById(taskId)
            if (task == null) {
                Log.w("TodoRepository", "Task not found for move: $taskId")
                return@dbWrite false
            }

            database.taskOrderOpsDao().moveToParent(taskId, newParentId, newIndex, Clock.System.now().toEpochMilliseconds())
            Log.d("TodoRepository", "Moved task $taskId to parent=$newParentId at index=${newIndex ?: "end"}")
            true
        }



    private fun logRowsUpdated(successMessage: String, missingMessage: String, rowsUpdated: Int): Boolean {
        return if (rowsUpdated > 0) {
            Log.d("TodoRepository", successMessage)
            true
        } else {
            Log.w("TodoRepository", missingMessage)
            false
        }
    }

    private fun cleanSubtaskTitles(subtaskTitles: List<String>): List<String> {
        return subtaskTitles.map { it.trim() }.filter { it.isNotBlank() }
    }

    private suspend fun updateStatusWithDoneCascade(id: Long, status: TaskStatus, updatedAt: Long): Int {
        val rowsUpdated = database.taskDao().updateStatus(id, status, updatedAt)
        if (rowsUpdated == 0 || status != TaskStatus.DONE) return rowsUpdated

        // Completion cascades only in the DONE direction: checking a parent means the
        // whole task tree is complete, while reopening a parent should not erase the
        // user's per-child completion state.
        val descendantIds = collectDescendantIds(id)
        if (descendantIds.isEmpty()) return rowsUpdated

        return rowsUpdated + database.taskDao().updateStatusForIds(descendantIds, status, updatedAt)
    }

    private suspend fun collectDescendantIds(parentId: Long): List<Long> {
        val descendantIds = mutableListOf<Long>()
        val visited = mutableSetOf(parentId)
        val pending = ArrayDeque<Long>()
        pending.add(parentId)

        while (pending.isNotEmpty()) {
            val currentParentId = pending.removeLast()
            val children = database.taskDao().getDirectChildren(currentParentId)
            children.forEach { child ->
                if (visited.add(child.id)) {
                    descendantIds.add(child.id)
                    pending.add(child.id)
                }
            }
        }

        return descendantIds
    }

    private suspend fun insertSubtasks(
        parentId: Long,
        subtaskTitles: List<String>,
        priority: Priority,
        dueAt: Instant?,
        now: Instant
    ): List<Long> {
        val firstOrder = (database.taskDao().getMaxOrderInParent(parentId) ?: -1L) + 1L
        return subtaskTitles.mapIndexed { index, subtaskTitle ->
            val subtask = Task(
                id = null,
                title = subtaskTitle,
                content = null,
                status = TaskStatus.OPEN,
                priority = priority,
                parentId = parentId,
                dueAt = dueAt,
                createdAt = now,
                updatedAt = now,
                orderInParent = firstOrder + index.toLong()
            )
            database.taskDao().insert(subtask.toEntity())
        }
    }
}

data class TaskTreeInsertResult(
    val parentId: Long,
    val subtaskIds: List<Long>
)

data class SubtaskDraft(
    val title: String,
    val content: String? = null,
    val priority: Priority = Priority.DEFAULT,
    val dueAt: Instant? = null,
    val estimatedOrder: Int? = null
)
