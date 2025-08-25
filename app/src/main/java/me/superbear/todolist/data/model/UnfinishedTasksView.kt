package me.superbear.todolist.data.model

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

/**
 * SQLite VIEW for unfinished (OPEN status) tasks.
 * 
 * This view simplifies queries for active tasks by pre-filtering tasks
 * with OPEN status and maintaining hierarchical ordering. Using a VIEW
 * is more efficient than repeatedly applying WHERE clauses in queries.
 * 
 * The view maintains the same structure as TaskEntity but only includes
 * tasks that are not yet completed, making it ideal for:
 * - Active task lists
 * - Progress tracking
 * - Task management UI that focuses on pending work
 * 
 * Ordering matches the main tasks table:
 * 1. parent_id - groups parent-child relationships
 * 2. order_in_parent - respects manual user ordering
 * 3. created_at - consistent tiebreaker
 */
@DatabaseView(
    viewName = "unfinished_tasks",
    value = """
        SELECT 
            id,
            title,
            content,
            status,
            priority,
            created_at,
            updated_at,
            due_at,
            parent_id,
            order_in_parent
        FROM tasks 
        WHERE status = 'OPEN'
        ORDER BY parent_id ASC, order_in_parent ASC, created_at ASC
    """
)
data class UnfinishedTasksView(
    val id: Long,
    val title: String,
    val content: String?,
    val status: me.superbear.todolist.domain.entities.TaskStatus,
    val priority: me.superbear.todolist.domain.entities.Priority,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long?,
    
    @ColumnInfo(name = "due_at")
    val dueAt: Long?,
    
    @ColumnInfo(name = "parent_id")
    val parentId: Long?,
    
    @ColumnInfo(name = "order_in_parent")
    val orderInParent: Long
)
