package me.superbear.todolist.assistant.subtask

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import android.util.Log
import kotlinx.serialization.Serializable
import me.superbear.todolist.domain.entities.Priority

/**
 * Subtask input structure passed directly by the LLM as a structured list parameter.
 * Using a typed list avoids the "JSON string inside JSON" double-serialisation pitfall
 * that caused Koog's JSONSerializer to fail when the LLM passed a JSON array as a
 * plain `String` argument.
 */
@Serializable
data class SubtaskInput(
    val title: String,
    val content: String? = null,
    val priority: String? = null,
    val estimatedOrder: Int? = null
)

@LLMDescription("Produces a task decomposition with ordered subtasks")
class DivideTaskToolSet : ToolSet {
    var lastResult: SubtaskDivisionResponse? = null

    @Tool
    @LLMDescription("Divide a task into ordered executable subtasks. Call this tool exactly once with all subtasks.")
    fun divide_task(
        @LLMDescription("Brief rationale for this decomposition")
        reasoning: String,
        @LLMDescription("Ordered list of subtasks to create")
        subtasks: List<SubtaskInput>
    ): String {
        if (subtasks.isEmpty()) {
            Log.e("DivideTaskToolSet", "divide_task called with empty subtasks list")
            return "Error: subtasks list must not be empty"
        }
        Log.d("DivideTaskToolSet", "divide_task called: ${subtasks.size} subtasks, reasoning='$reasoning'")
        val suggestions = subtasks.mapIndexed { index, input ->
            SubtaskSuggestion(
                title = input.title,
                content = input.content,
                priority = parsePriority(input.priority),
                estimatedOrder = input.estimatedOrder ?: (index + 1),
                dependencies = emptyList()
            )
        }
        lastResult = SubtaskDivisionResponse(
            originalTask = "",
            subtasks = suggestions,
            reasoning = reasoning
        )
        return "ok: ${suggestions.size} subtasks recorded"
    }

    private fun parsePriority(priorityStr: String?): Priority {
        return when (priorityStr?.uppercase()) {
            "HIGH" -> Priority.HIGH
            "MEDIUM" -> Priority.MEDIUM
            "LOW" -> Priority.LOW
            else -> Priority.DEFAULT
        }
    }
}
