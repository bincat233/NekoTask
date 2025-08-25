package me.superbear.todolist.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.domain.entities.TaskStatus

/**
 * Room Entity representing a task in the local database.
 * 
 * This entity mirrors the domain Task model but uses database-friendly types:
 * - Instant timestamps are stored as Long (epoch milliseconds)
 * - Enum types are stored directly (Room handles conversion automatically)
 * 
 * Indexes are optimized for hierarchical queries:
 * - parent_id: Fast parent-child relationship lookups
 * - status: Quick filtering by completion status
 * - (parent_id, order_in_parent): Efficient hierarchical ordering
 * - due_at: Optional index for future due-date filtering
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["parent_id"]),
        Index(value = ["status"]),
        Index(value = ["parent_id", "order_in_parent"]),
        Index(value = ["due_at"])
    ]
)
data class TaskEntity(
    /** Unique identifier for the task */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Task title (required) */
    @ColumnInfo(name = "title")
    val title: String,

    /** Optional task description/notes */
    @ColumnInfo(name = "content")
    val content: String? = null,

    /** Task completion status (OPEN/DONE) */
    @ColumnInfo(name = "status")
    val status: TaskStatus,

    /** Task priority level */
    @ColumnInfo(name = "priority")
    val priority: Priority,

    /** Creation timestamp (epoch milliseconds) */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Last update timestamp (epoch milliseconds, optional) */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long? = null,

    /** Due date timestamp (epoch milliseconds, optional) */
    @ColumnInfo(name = "due_at")
    val dueAt: Long? = null,

    /** Parent task ID for hierarchical structure (null for root tasks) */
    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,

    /** Order within the same parent (for manual reordering) */
    @ColumnInfo(name = "order_in_parent")
    val orderInParent: Long = 0
)
