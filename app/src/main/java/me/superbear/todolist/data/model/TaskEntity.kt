package me.superbear.todolist.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.domain.entities.TaskStatus

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String? = null,

    @ColumnInfo(name = "status")
    val status: TaskStatus,

    @ColumnInfo(name = "priority")
    val priority: Priority,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long? = null,

    @ColumnInfo(name = "due_at")
    val dueAt: Long? = null,

    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,

    @ColumnInfo(name = "order_in_parent")
    val orderInParent: Long = 0
)
