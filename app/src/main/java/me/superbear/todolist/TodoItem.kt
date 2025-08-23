package me.superbear.todolist

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Priority — task priority enum (similar to Java/JS enums).
 * - DEFAULT: not specified / normal
 * - LOW / MEDIUM / HIGH
 */
enum class Priority {
    LOW, MEDIUM, HIGH, DEFAULT
}

@Serializable
/**
 * Task — todo item data model (like a Java POJO / JS object).
 *
 * Pure data, no UI logic. Typically treated as immutable; use `copy(...)` to create an updated instance.
 *
 * @property id Unique identifier (Long). Used to find/update/delete a task.
 * @property title Title (required, short text).
 * @property createdAt Creation time (UTC timestamp, `kotlinx.datetime.Instant`).
 * @property notes Optional notes.
 * @property dueAt Optional due time. Serialized to string in snapshots.
 * @property priority Priority enum (LOW/MEDIUM/HIGH/DEFAULT).
 * @property status Status string, e.g. "OPEN" / "DONE"; used to split finished/unfinished.
 * @property aiInsights Optional AI-generated insights.
 * @property origin Optional origin, e.g. "manual" / "assistant".
 * @property updatedAt Optional last updated time.
 * @property parentId Optional parent task id. Use this as the single source of truth for hierarchy.
 */
data class Task(
    val id: Long,
    val title: String,
    val createdAt: Instant,
    val notes: String? = null,
    val dueAt: Instant? = null,
    val priority: Priority = Priority.DEFAULT,
    val status: String = "OPEN",
    val aiInsights: String? = null,
    val origin: String? = null,
    val updatedAt: Instant? = null,
    val parentId: Long? = null
)

