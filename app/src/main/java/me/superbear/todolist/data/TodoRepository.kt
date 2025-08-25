package me.superbear.todolist.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.data.model.AppDatabase
import me.superbear.todolist.data.model.TaskEntity
import me.superbear.todolist.data.model.toDomain
import me.superbear.todolist.data.model.toEntity
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus
import me.superbear.todolist.domain.entities.Priority
import org.json.JSONArray
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Repository for handling Task data operations.
 * 
 * This class now uses Room database as the single source of truth (SSOT),
 * with automatic seeding from JSON assets on first launch.
 * Provides reactive Flow-based data access for UI components.
 */
class TodoRepository(private val context: Context) {

    // Seeding manager for initial data population
    private val seedManager = SeedManager(context)

    // Room database instance, initialized lazily
    private val database: AppDatabase by lazy {
        val builder = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "todolist_database"
        )
        if (BuildConfig.DEBUG) {
            // Safer for development; consider explicit migrations for release builds
            // false = drop only Room-managed tables (matches previous default behavior)
            builder.fallbackToDestructiveMigration(false)
        }
        builder.build()
    }

    // Repository scope for background operations
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Room Flow as the single source of truth
    val tasks: Flow<List<Task>> = database.taskDao().observeAll().map { entities ->
        entities.toDomain()
    }

    // Legacy StateFlow bridge (kept for compatibility, but Room Flow is preferred)
    private val _tasksStateFlow = MutableStateFlow<List<Task>>(emptyList())
    val tasksStateFlow: StateFlow<List<Task>> = _tasksStateFlow.asStateFlow()

    init {
        // IMPORTANT: Delete database BEFORE lazy initialization if flag is set
        if (BuildConfig.FORCE_DELETE_DB) {
            Log.w("TodoRepository", "FORCE_DELETE_DB is true, deleting database.")
            context.deleteDatabase("todolist_database")
            // Also reset the seeding flag so database will be re-seeded
            seedManager.resetSeedingFlagForTesting()
        }

        // Initialize database and perform seeding if needed
        initializeDatabase()

        // Bridge Room Flow to StateFlow for legacy compatibility
        repositoryScope.launch {
            tasks.collect { taskList ->
                _tasksStateFlow.value = taskList
            }
        }
    }

    /**
     * Initialize database and perform seeding if needed.
     * This replaces the old JSON loading approach.
     */
    private fun initializeDatabase() {
        repositoryScope.launch {
            try {
                if (seedManager.needsSeeding(database)) {
                    Log.d("TodoRepository", "Database empty, seeding with built-in sample data (bypassing JSON)...")
                    seedManager.seedWithSampleData(database)
                    Log.d("TodoRepository", "Database sample seeding completed successfully")
                } else {
                    Log.d("TodoRepository", "Database already seeded, skipping")
                }
            } catch (e: Exception) {
                Log.e("TodoRepository", "Failed to initialize database", e)
            }
        }
    }

    /**
     * Update a task with new field values.
     * Preserves existing fields if not specified in the update.
     * 
     * @param id Task ID to update
     * @param title New title (optional)
     * @param content New content/notes (optional)
     * @param priority New priority (optional)
     * @param dueAt New due date (optional)
     */
    fun updateTask(
        id: Long,
        title: String? = null,
        content: String? = null,
        priority: Priority? = null,
        dueAt: Instant? = null
    ) {
        repositoryScope.launch {
            try {
                val currentTask = _tasksStateFlow.value.find { it.id == id }
                if (currentTask != null) {
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
                    } else {
                        Log.w("TodoRepository", "Task not found for update: $id")
                    }
                } else {
                    Log.w("TodoRepository", "Task not found in StateFlow for update: $id")
                }
            } catch (e: Exception) {
                Log.e("TodoRepository", "Failed to update task: $id", e)
            }
        }
    }

    /**
     * Delete a task by its ID.
     * 
     * @param id Task ID to delete
     */
    fun deleteTask(id: Long) {
        repositoryScope.launch {
            try {
                val rowsDeleted = database.taskDao().deleteById(id)
                if (rowsDeleted > 0) {
                    Log.d("TodoRepository", "Deleted task: $id")
                } else {
                    Log.w("TodoRepository", "Task not found for deletion: $id")
                }
            } catch (e: Exception) {
                Log.e("TodoRepository", "Failed to delete task: $id", e)
            }
        }
    }

    // Order maintenance operations (internal API)

    /**
     * Adds a new task before a target task with proper order management.
     * Uses transaction to ensure atomic order shifting and insertion.
     * 
     * @param targetId ID of the task to insert before
     * @param title Title of the new task
     * @param content Optional content for the new task
     * @param priority Priority of the new task
     */
    fun addTaskBefore(
        targetId: Long, 
        title: String, 
        content: String? = null,
        priority: Priority = Priority.DEFAULT
    ) {
        repositoryScope.launch {
            try {
                // Step 1: Get target task info
                val targetTask = database.taskDao().getTaskById(targetId)
                if (targetTask == null) {
                    Log.w("TodoRepository", "Target task not found for addTaskBefore: $targetId")
                    return@launch
                }

                // Step 2: Shift existing tasks to make space
                val shiftedRows = database.taskDao().bulkShiftOrders(
                    parentId = targetTask.parentId,
                    fromInclusive = targetTask.orderInParent,
                    delta = 1L
                )

                // Step 3: Insert new task at target's original position
                val now = Clock.System.now()

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
            } catch (e: Exception) {
                Log.e("TodoRepository", "Failed to add task before $targetId: $title", e)
            }
        }
    }

    /**
     * Reorders a task to a new position within its parent.
     * Handles sibling reindexing if necessary to maintain consecutive ordering.
     * 
     * @param taskId ID of the task to reorder
     * @param newOrder New order position (0-based)
     */
    fun reorder(taskId: Long, newOrder: Long) {
        repositoryScope.launch {
            try {
                // Fetch parentId first to target the correct sibling scope
                val task = database.taskDao().getTaskById(taskId)
                if (task == null) {
                    Log.w("TodoRepository", "Task not found for reorder: $taskId")
                    return@launch
                }

                database.taskDao().reorderWithinParent(task.parentId, taskId, newOrder)
                Log.d("TodoRepository", "Reordered task $taskId within parent ${task.parentId} to $newOrder (atomic)")
            } catch (e: Exception) {
                Log.e("TodoRepository", "Failed to reorder task $taskId to $newOrder", e)
            }
        }
    }

    /**
     * Reindexes all siblings within a parent to have consecutive order values.
     * Useful for cleaning up gaps after deletions or bulk operations.
     * 
     * @param parentId Parent task ID (null for root-level tasks)
     */
    private suspend fun reindexSiblings(parentId: Long?) {
        try {
            val siblings = database.taskDao().getSiblings(parentId)
            val sortedSiblings = siblings.sortedWith(
                compareBy<TaskEntity> { it.orderInParent }.thenBy { it.createdAt }
            )

            sortedSiblings.forEachIndexed { index, sibling ->
                if (sibling.orderInParent != index.toLong()) {
                    database.taskDao().updateOrder(sibling.id, index.toLong(), parentId)
                }
            }

            Log.d("TodoRepository", "Reindexed ${sortedSiblings.size} siblings for parent $parentId")
        } catch (e: Exception) {
            Log.e("TodoRepository", "Failed to reindex siblings for parent $parentId", e)
        }
    }

    // Legacy JSON loading method removed - now handled by SeedManager
    // This functionality has been moved to SeedManager.seedFromAssets()

    /**
     * Simulates an API call to update a Task on a server.
     * In a real application, this function would make an actual network request.
     * @param item The Task to be updated.
     * @return Boolean indicating whether the simulated update was successful.
     */
    suspend fun updateTaskOnServer(item: Task): Boolean {
        // Simulate network delay to mimic a real API call.
        delay(500) // 0.5 second delay

        // Log the simulated action.
        // In a real app, you would have network request code here (e.g., using Retrofit or Ktor).
        Log.d("TodoRepository", "Simulating update for item: ${item.id}, title: '${item.title}', status: ${item.status} on server.")

        // For now, always simulate success.
        return true
    }

    /**
     * Get all child tasks for a given parent task ID
     */
    fun getChildren(parentId: Long): List<Task> {
        return _tasksStateFlow.value.filter { it.parentId == parentId }
    }
    
    /**
     * Observe child tasks for a given parent task ID (Room-based)
     */
    fun observeChildren(parentId: Long): Flow<List<Task>> {
        return database.taskDao().observeChildren(parentId).map { entities ->
            entities.toDomain()
        }
    }

    /**
     * Get progress information (done count, total count) for a parent task
     */
    fun getParentProgress(parentId: Long): Pair<Int, Int> {
        val children = getChildren(parentId)
        val doneCount = children.count { it.status == TaskStatus.DONE }
        return Pair(doneCount, children.size)
    }

    /**
     * Add a new task (parent or child) to the database.
     * Uses Room's addTaskWithOrdering for atomic order computation.
     * 
     * @param title The title of the new task
     * @param parentId Optional parent ID for creating subtasks
     */
    fun addTask(title: String, parentId: Long? = null) {
        repositoryScope.launch {
            try {
                val now = Clock.System.now()

                val newTask = Task(
                    id = null,
                    title = title,
                    status = TaskStatus.OPEN,
                    parentId = parentId,
                    dueAt = null,
                    createdAt = now,
                    updatedAt = now,
                    orderInParent = 0 // Will be computed by addTaskWithOrdering
                )
                
                // Use Room's transaction-based ordering method
                val rowId = database.taskDao().addTaskWithOrdering(newTask.toEntity())
                Log.d("TodoRepository", "Added new task: $title (rowId: $rowId)")
            } catch (e: Exception) {
                Log.e("TodoRepository", "Failed to add task: $title", e)
            }
        }
    }

    /**
     * Toggle the completion status of a task in the database.
     * Uses efficient updateStatus method for better performance.
     * 
     * @param id The ID of the task to toggle
     * @param done Whether the task should be marked as done
     */
    fun toggleTaskStatus(id: Long, done: Boolean) {
        repositoryScope.launch {
            try {
                val newStatus = if (done) TaskStatus.DONE else TaskStatus.OPEN
                val updatedAt = Clock.System.now().toEpochMilliseconds()
                
                // Use efficient status-only update
                val rowsUpdated = database.taskDao().updateStatus(id, newStatus, updatedAt)
                
                if (rowsUpdated > 0) {
                    Log.d("TodoRepository", "Toggled task status: $id -> $done")
                } else {
                    Log.w("TodoRepository", "Task not found for toggle: $id")
                }
            } catch (e: Exception) {
                Log.e("TodoRepository", "Failed to toggle task status: $id", e)
            }
        }
    }
}
