package me.superbear.todolist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class AssistantActionParser {

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

    private val fencedRegex = Regex("""```(json|JSON)?\s*([\s\S]*?)\s*```""", RegexOption.MULTILINE)

    fun parse(text: String): Result<List<AssistantAction>> = runCatching {
        val jsonText = extractJson(text) ?: return Result.success(emptyList())
        val dto = json.decodeFromString<ActionListDto>(jsonText)
        val actions = dto.actions.mapNotNull { a ->
            when (a.type.lowercase()) {
                "add_task" -> a.title.trim().takeIf { it.isNotEmpty() }?.let { t ->
                    AssistantAction.AddTask(
                        title = t,
                        notes = a.notes?.trim().takeIf { !it.isNullOrEmpty() },
                        dueAtIso = a.dueAtIso?.trim().takeIf { !it.isNullOrEmpty() },
                        priority = a.priority?.trim().takeIf { !it.isNullOrEmpty() }
                    )
                }
                else -> null
            }
        }
        actions
    }.recover { /* Log.w("Parser", "parse failed", it) */ emptyList() }
        .mapCatching { it } // 保持 Result<List<AssistantAction>>

    private fun extractJson(text: String): String? {
        fencedRegex.find(text)?.let { return it.groupValues[2].trim() }
        val first = text.indexOf('{'); val last = text.lastIndexOf('}')
        return if (first in 0..<last) text.substring(first, last + 1) else null
    }

}