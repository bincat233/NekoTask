package me.superbear.todolist.data.model

import kotlinx.datetime.Instant
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus

/**
 * Mapping functions between domain Task and database TaskEntity.
 * 
 * Handles conversion between different data representations:
 * - Instant <-> Long (epoch milliseconds)
 * - Domain Task <-> Database TaskEntity
 */

/**
 * Converts a domain Task to a database TaskEntity.
 * 
 * @return TaskEntity suitable for database storage
 */
fun Task.toEntity(): TaskEntity {
    return TaskEntity(
        id = id ?: 0,
        title = title,
        content = content,
        status = status,
        priority = priority,
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt?.toEpochMilliseconds(),
        dueAt = dueAt?.toEpochMilliseconds(),
        parentId = parentId,
        orderInParent = orderInParent
    )
}

/**
 * Converts a database TaskEntity to a domain Task.
 * 
 * @return Domain Task for business logic and UI
 */
fun TaskEntity.toDomain(): Task {
    return Task(
        id = id,
        title = title,
        content = content,
        status = status,
        priority = priority,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) },
        dueAt = dueAt?.let { Instant.fromEpochMilliseconds(it) },
        parentId = parentId,
        orderInParent = orderInParent
    )
}

/**
 * Converts a list of TaskEntity to domain Tasks.
 */
fun List<TaskEntity>.toDomain(): List<Task> = map { it.toDomain() }

/**
 * Converts a list of domain Tasks to TaskEntity.
 */
fun List<Task>.toEntity(): List<TaskEntity> = map { it.toEntity() }
