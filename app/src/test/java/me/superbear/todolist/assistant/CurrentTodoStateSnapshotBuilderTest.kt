package me.superbear.todolist.assistant

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentTodoStateSnapshotBuilderTest {
    private val now = Instant.parse("2026-07-12T12:00:00Z")

    @Test
    fun buildsNestedSnapshotWithProgress() {
        val tasks = listOf(
            task(id = 1, title = "Parent"),
            task(id = 2, title = "Done child", parentId = 1, status = TaskStatus.DONE),
            task(id = 3, title = "Open child", parentId = 1),
            task(id = 4, title = "Finished root", status = TaskStatus.DONE)
        )

        val snapshot = Json.parseToJsonElement(
            CurrentTodoStateSnapshotBuilder().build(tasks, now)
        ).jsonObject

        assertEquals(now.toString(), snapshot["now"]!!.jsonPrimitive.content)
        assertEquals(2, snapshot["finished_count"]!!.jsonPrimitive.content.toInt())

        val parent = snapshot["unfinished"]!!.jsonArray[0].jsonObject
        assertEquals("Parent", parent["title"]!!.jsonPrimitive.content)
        assertEquals(2, parent["totalChildren"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, parent["doneChildren"]!!.jsonPrimitive.content.toInt())
        assertEquals(0.5f, parent["progress"]!!.jsonPrimitive.float)

        val openChildren = parent["children"]!!.jsonArray
        assertEquals(1, openChildren.size)
        assertEquals("Open child", openChildren[0].jsonObject["title"]!!.jsonPrimitive.content)

        val finishedRoot = snapshot["finished"]!!.jsonArray[0].jsonObject
        assertEquals("Finished root", finishedRoot["title"]!!.jsonPrimitive.content)
    }

    private fun task(
        id: Long,
        title: String,
        parentId: Long? = null,
        status: TaskStatus = TaskStatus.OPEN
    ): Task {
        return Task(
            id = id,
            title = title,
            createdAt = now,
            updatedAt = now,
            status = status,
            priority = Priority.DEFAULT,
            parentId = parentId
        )
    }
}
