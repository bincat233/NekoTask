package me.superbear.todolist

import android.util.Log
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
        val title: String,
        val notes: String? = null,
        @SerialName("dueAt")
        val dueAtIso: String? = null,
        val priority: String? = null
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val fencedRegex = Regex("""```(json|JSON)?\s*([\sS]*?)\s*```""", RegexOption.MULTILINE)

    fun parseEnvelope(text: String): Result<AssistantEnvelope> {
        Log.d("AssistantActionParser", "Received data: $text")
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
            "add_task" -> dto.title.trim().takeIf { it.isNotEmpty() }?.let { t ->
                AssistantAction.AddTask(
                    title = t,
                    notes = dto.notes?.trim().takeIf { !it.isNullOrEmpty() },
                    dueAtIso = dto.dueAtIso?.trim().takeIf { !it.isNullOrEmpty() },
                    priority = dto.priority?.trim().takeIf { !it.isNullOrEmpty() }
                )
            }
            else -> null
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
    fun build(tasks: List<Task>, maxUnfinished: Int = 20): String {
        val now = Clock.System.now().toString()
        val unfinishedTasks = tasks.filter { it.status != "DONE" }
        val finishedCount = tasks.size - unfinishedTasks.size

        val unfinishedJson = buildJsonArray {
            unfinishedTasks.take(maxUnfinished).forEach { task ->
                add(buildJsonObject {
                    put("id", task.id)
                    put("title", task.title.take(100)) // Trim title
                    task.dueAtIso?.let { put("dueAt", it) }
                    task.priority?.let { put("priority", it) }
                })
            }
        }

        val root = buildJsonObject {
            put("now", now)
            put("unfinished", unfinishedJson)
            put("finished_count", finishedCount)
        }
        return root.toString()
    }
}
