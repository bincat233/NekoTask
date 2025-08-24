package me.superbear.todolist.assistant

import android.util.Log
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.superbear.todolist.domain.entities.Task

class AssistantActionParser {

    @Serializable
    private data class EnvelopeDto(
        val say: String? = null,
        val actions: List<ActionDto>? = null
    )

    @Serializable
    private data class ActionListDto(
        val actions: List<ActionDto>
    )

    @Serializable
    private data class ActionDto(
        val type: String,
        val id: Long? = null,
        val title: String? = null,
        val notes: String? = null,
        @SerialName("dueAt")
        val dueAtIso: String? = null,
        val priority: String? = null,
        val parentId: Long? = null
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val fencedRegex = Regex("""´´´(json|JSON)?\s*([\s\S]*?)\s*´´´""", RegexOption.MULTILINE)

    fun parseEnvelope(text: String): Result<AssistantEnvelope> {
        // Pretty-print JSON (if possible) to make logs easier to read
        val prettySample = runCatching {
            val jsonText = extractJson(text) ?: text
            val element = Json.parseToJsonElement(jsonText)
            val pretty = Json { prettyPrint = true }
            pretty.encodeToString(element)
        }.getOrElse { text }
        Log.d("AssistantActionParser", "Received data:\n$prettySample")
        return runCatching {
            val jsonText = extractJson(text)

            if (jsonText.isNullOrEmpty()) {
                return@runCatching AssistantEnvelope(say = text)
            }

            // Try parsing as EnvelopeDto (new format)
            runCatching { json.decodeFromString<EnvelopeDto>(jsonText) }.getOrNull()?.let { dto ->
                return@runCatching AssistantEnvelope(
                    say = dto.say,
                    actions = dto.actions?.mapNotNull(::mapAction) ?: emptyList()
                )
            }

            // Try parsing as ActionListDto (old format)
            runCatching { json.decodeFromString<ActionListDto>(jsonText) }.getOrNull()?.let { dto ->
                return@runCatching AssistantEnvelope(
                    say = null,
                    actions = dto.actions.mapNotNull(::mapAction)
                )
            }

            // Try parsing as single ActionDto (old format)
            runCatching { json.decodeFromString<ActionDto>(jsonText) }.getOrNull()?.let { dto ->
                val action = mapAction(dto)
                return@runCatching AssistantEnvelope(
                    say = null,
                    actions = if (action != null) listOf(action) else emptyList()
                )
            }

            // If all parsing fails, return the original text as the "say" part
            AssistantEnvelope(say = text)
        }.recover {
            // If any parsing throws an unhandled exception, recover by returning the text.
            AssistantEnvelope(say = text)
        }
    }


    private fun mapAction(dto: ActionDto): AssistantAction? {
        return when (dto.type.lowercase()) {
            "add_task" -> {
                val t = dto.title?.trim()?.takeIf { it.isNotEmpty() }
                if (t == null) {
                    Log.e("AssistantActionParser", "add_task missing required 'title': $dto")
                    null
                } else {
                    AssistantAction.AddTask(
                        title = t,
                        notes = dto.notes?.trim().takeIf { !it.isNullOrEmpty() },
                        dueAtIso = dto.dueAtIso?.trim().takeIf { !it.isNullOrEmpty() },
                        priority = dto.priority?.trim().takeIf { !it.isNullOrEmpty() },
                        parentId = dto.parentId
                    )
                }
            }
            "delete_task" -> {
                val id = dto.id
                if (id == null) {
                    Log.e("AssistantActionParser", "delete_task missing required 'id': $dto")
                    null
                } else AssistantAction.DeleteTask(id = id)
            }
            "update_task" -> {
                val id = dto.id
                if (id == null) {
                    Log.e("AssistantActionParser", "update_task missing required 'id': $dto")
                    null
                } else {
                    AssistantAction.UpdateTask(
                        id = id,
                        title = dto.title?.trim().takeIf { !it.isNullOrEmpty() },
                        notes = dto.notes?.trim().takeIf { !it.isNullOrEmpty() },
                        dueAtIso = dto.dueAtIso?.trim().takeIf { !it.isNullOrEmpty() },
                        priority = dto.priority?.trim().takeIf { !it.isNullOrEmpty() },
                        parentId = dto.parentId
                    )
                }
            }
            "complete_task" -> {
                val id = dto.id
                if (id == null) {
                    Log.e("AssistantActionParser", "complete_task missing required 'id': $dto")
                    null
                } else AssistantAction.CompleteTask(id = id)
            }
            else -> {
                Log.e("AssistantActionParser", "Unknown action type: '${dto.type}' in $dto")
                null
            }
        }
    }

    private fun extractJson(text: String): String? {
        return fencedRegex.find(text)?.let {
            it.groupValues[2].trim()
        } ?: run {
            val first = text.indexOf('{')
            val last = text.lastIndexOf('}')
            if (first != -1 && last != -1 && first < last) {
                text.substring(first, last + 1)
            } else {
                null
            }
        }
    }
}

object TaskStateSnapshotBuilder {
    fun build(tasks: List<Task>, maxUnfinished: Int = 20, maxFinished: Int = 20): String {
        val now = Clock.System.now().toString()
        val unfinishedTasks = tasks.filter { it.status != "DONE" }
        val finishedTasks = tasks.filter { it.status == "DONE" }

        val unfinishedJson = buildJsonArray {
            unfinishedTasks.take(maxUnfinished).forEach { task ->
                add(buildJsonObject {
                    put("id", task.id)
                    put("title", task.title.take(100)) // Trim title
                    task.dueAt?.let { put("dueAt", it.toString()) }
                    put("priority", task.priority.name)
                })
            }
        }

        val finishedJson = buildJsonArray {
            finishedTasks.take(maxFinished).forEach { task ->
                add(buildJsonObject {
                    put("id", task.id)
                    put("title", task.title.take(100))
                })
            }
        }

        val root = buildJsonObject {
            put("now", now)
            put("unfinished", unfinishedJson)
            put("finished", finishedJson)
            put("finished_count", finishedTasks.size)
        }
        return root.toString()
    }
}
