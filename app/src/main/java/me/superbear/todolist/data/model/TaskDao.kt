package me.superbear.todolist.data.model

import androidx.room.Dao
import androidx.room.Query
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
}
