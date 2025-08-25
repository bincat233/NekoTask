package me.superbear.todolist.data.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.superbear.todolist.domain.entities.TaskStatus

/**
 * Data Access Object (DAO) for Task entities.
 * 
 * Provides reactive queries using Kotlin Flow for automatic UI updates.
 * All queries are optimized for hierarchical task structures with proper ordering:
 * - Primary sort: parent_id (groups parent-child relationships)
 * - Secondary sort: order_in_parent (manual user ordering)
 * - Tertiary sort: created_at (consistent tiebreaker)
 */
@Dao
interface TaskDao {
    /**
     * Observes all tasks with hierarchical ordering.
     * 
     * Returns tasks ordered by:
     * 1. parent_id - groups parent tasks with their children
     * 2. order_in_parent - respects manual user ordering within each parent
     * 3. created_at - consistent tiebreaker for same order values
     */
    @Query("SELECT * FROM tasks ORDER BY parent_id ASC, order_in_parent ASC, created_at ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    /**
     * Observes child tasks of a specific parent.
     * 
     * @param parentId Parent task ID (null for root-level tasks)
     * @return Flow of child tasks ordered by order_in_parent, then created_at
     */
    @Query("""
        SELECT * FROM tasks 
        WHERE ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
        ORDER BY order_in_parent ASC, created_at ASC
    """)
    fun observeChildren(parentId: Long?): Flow<List<TaskEntity>>

    /**
     * Gets the maximum order value within a parent for inserting new tasks at the end.
     * 
     * @param parentId Parent task ID (null for root-level tasks)
     * @return Maximum order_in_parent value, or null if no children exist
     */
    @Query("""
        SELECT MAX(order_in_parent) FROM tasks 
        WHERE ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
    """)
    fun getMaxOrderInParent(parentId: Long?): Long?

    /**
     * Observes only unfinished (OPEN) tasks with hierarchical ordering.
     * 
     * Useful for UI sections that show only active tasks.
     * Maintains parent-child relationships in the ordering.
     */
    @Query("SELECT * FROM tasks WHERE status = 'OPEN' ORDER BY parent_id ASC, order_in_parent ASC, created_at ASC")
    fun observeUnfinished(): Flow<List<TaskEntity>>

    /**
     * Observes only finished (DONE) tasks with hierarchical ordering.
     * 
     * Useful for UI sections that show completed tasks or archive views.
     * Maintains parent-child relationships in the ordering.
     */
    @Query("SELECT * FROM tasks WHERE status = 'DONE' ORDER BY parent_id ASC, order_in_parent ASC, created_at ASC")
    fun observeFinished(): Flow<List<TaskEntity>>

    // Write operations for data management

    /**
     * Inserts a single task into the database.
     * 
     * @param task TaskEntity to insert
     * @return Row ID of the inserted task
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    /**
     * Updates an existing task in the database.
     * 
     * @param task TaskEntity with updated values
     * @return Number of rows updated (should be 1 for success)
     */
    @Update
    suspend fun update(task: TaskEntity): Int

    /**
     * Updates only the status and updatedAt fields of a task.
     * More efficient than updating the entire entity.
     * 
     * @param id Task ID to update
     * @param status New task status
     * @param updatedAt Updated timestamp (epoch milliseconds)
     * @return Number of rows updated
     */
    @Query("UPDATE tasks SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TaskStatus, updatedAt: Long): Int

    /**
     * Deletes a task by its ID.
     * 
     * @param id Task ID to delete
     * @return Number of rows deleted
     */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /**
     * Deletes a task entity.
     * 
     * @param task TaskEntity to delete
     * @return Number of rows deleted
     */
    @Delete
    suspend fun delete(task: TaskEntity): Int

    /**
     * Inserts multiple tasks in a single transaction.
     * Used primarily for seeding from JSON assets.
     * 
     * @param tasks List of TaskEntity objects to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    /**
     * Counts the total number of tasks in the database.
     * Used to check if seeding is needed.
     * 
     * @return Total task count
     */
    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTaskCount(): Int

    /**
     * Seeds tasks from JSON assets in a single transaction.
     * Ensures data consistency during the seeding process.
     * 
     * @param tasks List of tasks to seed
     */
    @Transaction
    suspend fun seedTasks(tasks: List<TaskEntity>) {
        insertTasks(tasks)
    }

    /**
     * Adds a new task with proper ordering in a transaction.
     * Computes the next order value and inserts the task atomically.
     * 
     * @param task TaskEntity to add (orderInParent will be computed)
     * @return Row ID of the inserted task
     */
    @Transaction
    suspend fun addTaskWithOrdering(task: TaskEntity): Long {
        val maxOrder = getMaxOrderInParent(task.parentId) ?: -1
        val taskWithOrder = task.copy(orderInParent = maxOrder + 1L)
        return insert(taskWithOrder)
    }

    // Order maintenance operations

    /**
     * Updates the order of a specific task.
     * 
     * @param id Task ID to update
     * @param newOrder New order value
     * @param parentId Parent ID for validation (optional)
     * @return Number of rows updated
     */
    @Query("UPDATE tasks SET order_in_parent = :newOrder WHERE id = :id AND (:parentId IS NULL OR parent_id = :parentId)")
    suspend fun updateOrder(id: Long, newOrder: Long, parentId: Long? = null): Int

    /**
     * Bulk shifts order values for tasks within a parent.
     * Used to make space for insertions or handle deletions.
     * 
     * @param parentId Parent task ID (null for root-level tasks)
     * @param fromInclusive Starting order value (inclusive)
     * @param delta Amount to shift (+1 to make space, -1 to close gaps)
     * @return Number of rows updated
     */
    @Query("""
        UPDATE tasks 
        SET order_in_parent = order_in_parent + :delta 
        WHERE ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
        AND order_in_parent >= :fromInclusive
    """)
    suspend fun bulkShiftOrders(parentId: Long?, fromInclusive: Long, delta: Long): Int

    /**
     * Reorders a task within its parent atomically.
     * Reads siblings, moves the task to the new position, and writes back
     * consecutive order values in a single transaction to avoid partial updates.
     *
     * @param parentId Parent task ID (null for root-level tasks)
     * @param taskId ID of the task to move
     * @param newOrder New order position (0-based)
     */
    @Transaction
    suspend fun reorderWithinParent(parentId: Long?, taskId: Long, newOrder: Long) {
        // Fetch siblings ordered by order_in_parent then created_at for stable ordering
        val siblings = getSiblings(parentId).sortedWith(
            compareBy<TaskEntity> { it.orderInParent }.thenBy { it.createdAt }
        )

        if (siblings.isEmpty()) return

        val currentIndex = siblings.indexOfFirst { it.id == taskId }
        if (currentIndex == -1) return

        val mutable = siblings.toMutableList()
        val taskToMove = mutable.removeAt(currentIndex)

        val clampedNew = newOrder.coerceIn(0L, mutable.size.toLong()).toInt()
        mutable.add(clampedNew, taskToMove)

        // Persist consecutive order values
        mutable.forEachIndexed { index, sibling ->
            if (sibling.orderInParent != index.toLong()) {
                updateOrder(sibling.id, index.toLong(), parentId)
            }
        }
    }

    /**
     * Gets a task by its ID for ordering operations.
     * 
     * @param id Task ID
     * @return TaskEntity or null if not found
     */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    /**
     * Gets all siblings of a parent ordered by their current order.
     * Used by Repository for reindexing operations.
     * 
     * @param parentId Parent task ID (null for root-level tasks)
     * @return List of sibling tasks ordered by order_in_parent, then created_at
     */
    @Query("""
        SELECT * FROM tasks 
        WHERE ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
        ORDER BY order_in_parent ASC, created_at ASC
    """)
    suspend fun getSiblings(parentId: Long?): List<TaskEntity>

    // ========================================
    // Convenience Queries for Parent Progress and Nested Fetch
    // ========================================

    /**
     * Observes a parent task with all its children using Room's @Relation.
     * This is more efficient than separate queries for parent and children.
     * 
     * @param parentId The parent task ID
     * @return Flow of TaskWithChildren containing parent and all children
     */
    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :parentId")
    fun observeParentWithChildren(parentId: Long): Flow<TaskWithChildren?>

    /**
     * Gets a parent task with all its children as a one-time fetch.
     * 
     * @param parentId The parent task ID
     * @return TaskWithChildren containing parent and all children, or null if parent not found
     */
    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :parentId")
    suspend fun getParentWithChildren(parentId: Long): TaskWithChildren?

    /**
     * Counts the number of completed children for a given parent.
     * Useful for progress tracking and completion statistics.
     * 
     * @param parentId The parent task ID
     * @return Number of children with DONE status
     */
    @Query("""
        SELECT COUNT(*) FROM tasks 
        WHERE parent_id = :parentId AND status = 'DONE'
    """)
    suspend fun countChildrenDone(parentId: Long): Int

    /**
     * Counts the total number of children for a given parent.
     * 
     * @param parentId The parent task ID
     * @return Total number of children
     */
    @Query("""
        SELECT COUNT(*) FROM tasks 
        WHERE parent_id = :parentId
    """)
    suspend fun countTotalChildren(parentId: Long): Int

    /**
     * Gets progress information for a parent task as a pair (done, total).
     * This is a convenience method that combines the two count queries.
     * 
     * @param parentId The parent task ID
     * @return Pair of (completed children count, total children count)
     */
    suspend fun getParentProgress(parentId: Long): Pair<Int, Int> {
        val doneCount = countChildrenDone(parentId)
        val totalCount = countTotalChildren(parentId)
        return doneCount to totalCount
    }

    /**
     * Observes all parent tasks that have children, along with their children.
     * Useful for displaying parent tasks with progress indicators.
     * 
     * @return Flow of list of TaskWithChildren for all parents that have children
     */
    @Transaction
    @Query("""
        SELECT * FROM tasks 
        WHERE id IN (SELECT DISTINCT parent_id FROM tasks WHERE parent_id IS NOT NULL)
        ORDER BY order_in_parent ASC, created_at ASC
    """)
    fun observeAllParentsWithChildren(): Flow<List<TaskWithChildren>>

    // ========================================
    // SQLite VIEW Queries for Performance
    // ========================================

    /**
     * Observes unfinished tasks using the pre-filtered unfinished_tasks VIEW.
     * More efficient than the regular observeUnfinished() method as filtering
     * is done at the database level through the VIEW.
     * 
     * @return Flow of unfinished tasks with hierarchical ordering
     */
    @Query("SELECT * FROM unfinished_tasks")
    fun observeUnfinishedFromView(): Flow<List<UnfinishedTasksView>>

    /**
     * Gets unfinished children of a specific parent using the VIEW.
     * 
     * @param parentId Parent task ID (null for root-level tasks)
     * @return Flow of unfinished child tasks
     */
    @Query("""
        SELECT * FROM unfinished_tasks 
        WHERE ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
    """)
    fun observeUnfinishedChildren(parentId: Long?): Flow<List<UnfinishedTasksView>>

    /**
     * Counts unfinished tasks using the VIEW for better performance.
     * 
     * @return Total count of unfinished tasks
     */
    @Query("SELECT COUNT(*) FROM unfinished_tasks")
    suspend fun countUnfinishedTasks(): Int

    /**
     * Counts unfinished children for a specific parent using the VIEW.
     * 
     * @param parentId Parent task ID
     * @return Count of unfinished children
     */
    @Query("""
        SELECT COUNT(*) FROM unfinished_tasks 
        WHERE ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
    """)
    suspend fun countUnfinishedChildren(parentId: Long?): Int

    // Legacy method name for backward compatibility
    suspend fun insertTask(task: TaskEntity): Long = insert(task)
}

// =============================
// Additional transactional helpers for ordering & reparenting
// =============================

@Dao
interface TaskOrderOpsDao {
    /**
     * Internal helper to update both parent_id and order_in_parent in one statement.
     */
    @Query("UPDATE tasks SET parent_id = :newParentId, order_in_parent = :newOrder WHERE id = :taskId")
    suspend fun updateParentAndOrder(taskId: Long, newParentId: Long?, newOrder: Long): Int

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Query("""
        SELECT * FROM tasks 
        WHERE ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
        ORDER BY order_in_parent ASC, created_at ASC
    """)
    suspend fun getSiblings(parentId: Long?): List<TaskEntity>

    @Query("""
        UPDATE tasks 
        SET order_in_parent = order_in_parent + :delta 
        WHERE ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
        AND order_in_parent >= :fromInclusive
    """)
    suspend fun bulkShiftOrders(parentId: Long?, fromInclusive: Long, delta: Long): Int

    @Transaction
    suspend fun reindexParent(parentId: Long?) {
        val siblings = getSiblings(parentId)
        siblings.forEachIndexed { index, s ->
            if (s.orderInParent != index.toLong()) {
                // Only update when value changes to reduce writes
                updateOrderInternal(s.id, index.toLong())
            }
        }
    }

    @Query("UPDATE tasks SET order_in_parent = :newOrder WHERE id = :id")
    suspend fun updateOrderInternal(id: Long, newOrder: Long): Int

    /**
     * Moves a task to a new parent, optionally to a specific index, atomically.
     * If newIndex is null, appends to end.
     */
    @Transaction
    suspend fun moveToParent(taskId: Long, newParentId: Long?, newIndex: Long?) {
        val task = getTaskById(taskId) ?: return
        val oldParentId = task.parentId
        val oldOrder = task.orderInParent

        if (oldParentId == newParentId) {
            // Same parent: if index provided, just reorder within parent using reindex algorithm
            val siblings = getSiblings(newParentId)
            val currentIndex = siblings.indexOfFirst { it.id == taskId }
            if (currentIndex == -1) return
            val mutable = siblings.toMutableList()
            val item = mutable.removeAt(currentIndex)
            val insertAt = newIndex?.coerceIn(0L, mutable.size.toLong())?.toInt() ?: mutable.size
            mutable.add(insertAt, item)
            mutable.forEachIndexed { idx, s ->
                if (s.orderInParent != idx.toLong()) updateOrderInternal(s.id, idx.toLong())
            }
            return
        }

        // 1) Close gap in old parent (shift down from oldOrder+1)
        bulkShiftOrders(oldParentId, oldOrder + 1L, -1L)

        // 2) Decide insert position in new parent
        val newSiblings = getSiblings(newParentId)
        val insertAt = newIndex?.coerceIn(0L, newSiblings.size.toLong())
            ?: newSiblings.size.toLong() // append

        // 3) Make space in new parent if inserting not at end
        if (insertAt < newSiblings.size.toLong()) {
            bulkShiftOrders(newParentId, insertAt, 1L)
        }

        // 4) Update the task's parent and order
        updateParentAndOrder(taskId, newParentId, insertAt)
    }
}
