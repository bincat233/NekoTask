package me.superbear.todolist.assistant

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus

/**
 * Builds the CURRENT_TODO_STATE JSON prompt context consumed by the assistant.
 */
class CurrentTodoStateSnapshotBuilder(
    private val json: Json = Json { prettyPrint = true }
) {
    @Serializable
    private data class Snapshot(
        val now: String,
        val unfinished: List<Node>,
        val finished: List<Node>,
        val finished_count: Int
    )

    @Serializable
    private data class Node(
        val id: Long,
        val title: String,
        val status: String,
        val priority: String,
        val dueAt: String? = null,
        val children: List<Node> = emptyList(),
        val totalChildren: Int = 0,
        val doneChildren: Int = 0,
        val progress: Float = 0f
    )

    fun build(tasks: List<Task>, now: Instant = Clock.System.now()): String {
        val openTasks = tasks.filter { it.status == TaskStatus.OPEN }
        val doneTasks = tasks.filter { it.status == TaskStatus.DONE }

        val openChildrenMap = openTasks.groupBy { it.parentId }
        val doneChildrenMap = doneTasks.groupBy { it.parentId }
        val allChildrenMap = tasks.groupBy { it.parentId }

        fun allDescendants(id: Long): List<Task> {
            val result = mutableListOf<Task>()
            fun dfs(currId: Long) {
                val kids = allChildrenMap[currId] ?: emptyList()
                for (kid in kids) {
                    result += kid
                    kid.id?.let { dfs(it) }
                }
            }
            dfs(id)
            return result
        }

        fun Task.toNode(childrenMap: Map<Long?, List<Task>>): Node {
            val childTasks = childrenMap[id] ?: emptyList()
            val childNodes = childTasks.map { it.toNode(childrenMap) }

            val taskId = id ?: -1L
            val descendants = if (taskId != -1L) allDescendants(taskId) else emptyList()
            val totalChildren = descendants.size
            val doneChildren = descendants.count { it.status == TaskStatus.DONE }
            val progress = if (totalChildren > 0) doneChildren.toFloat() / totalChildren else 0f

            return Node(
                id = taskId,
                title = title,
                status = status.name,
                priority = priority.name,
                dueAt = dueAt?.toString(),
                children = childNodes,
                totalChildren = totalChildren,
                doneChildren = doneChildren,
                progress = progress
            )
        }

        fun buildTree(rootCandidates: List<Task>, childrenMap: Map<Long?, List<Task>>): List<Node> {
            return rootCandidates.filter { it.parentId == null }.map { it.toNode(childrenMap) }
        }

        return json.encodeToString(
            Snapshot(
                now = now.toString(),
                unfinished = buildTree(openTasks, openChildrenMap),
                finished = buildTree(doneTasks, doneChildrenMap),
                finished_count = doneTasks.size
            )
        )
    }
}
