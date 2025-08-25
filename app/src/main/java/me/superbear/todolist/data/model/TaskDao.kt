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
    @Query("SELECT * FROM tasks WHERE parent_id = :parentId ORDER BY order_in_parent ASC, created_at ASC")
    fun observeChildren(parentId: Long?): Flow<List<TaskEntity>>

    /**
     * Gets the maximum order value within a parent for inserting new tasks at the end.
     * 
     * @param parentId Parent task ID (null for root-level tasks)
     * @return Maximum order_in_parent value, or null if no children exist
     */
    @Query("SELECT MAX(order_in_parent) FROM tasks WHERE parent_id = :parentId")
    fun getMaxOrderInParent(parentId: Long?): Int?

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

    // Legacy method name for backward compatibility
    suspend fun insertTask(task: TaskEntity): Long = insert(task)
}
