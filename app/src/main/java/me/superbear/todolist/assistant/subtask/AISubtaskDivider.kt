package me.superbear.todolist.assistant.subtask

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import android.util.Log
import me.superbear.todolist.domain.entities.Priority

class AISubtaskDivider(
    private val promptExecutor: PromptExecutor,
    private val model: LLModel
) : SubtaskDivider {

    private val toolSet = DivideTaskToolSet()

    override suspend fun divideTask(request: SubtaskDivisionRequest): Result<SubtaskDivisionResponse> {
        Log.d("DIAG", "[DIAG] AISubtaskDivider.divideTask ENTER: task='${request.taskTitle}' model=${model.id}")
        val systemPrompt = """
You are a task decomposition assistant.
Break the given task into concrete, executable subtasks.
You MUST call the divide_task tool exactly once with:
- reasoning: a brief explanation of your decomposition approach
- subtasks: a list of subtask objects, each with title (required), content (optional notes), priority (HIGH/MEDIUM/LOW/DEFAULT), and estimatedOrder (starting from 1)
Do NOT respond with plain text. Always call the divide_task tool.
        """.trimIndent()
        val userPrompt = buildUserPrompt(request)

        return try {
            val agent = AIAgent(
                promptExecutor = promptExecutor,
                llmModel = model,
                toolRegistry = ToolRegistry { tools(toolSet) },
                systemPrompt = systemPrompt,
                maxIterations = 5
            )

            Log.d("DIAG", "[DIAG] AISubtaskDivider.divideTask: running agent...")
            agent.run(userPrompt)
            Log.d("DIAG", "[DIAG] AISubtaskDivider.divideTask: agent.run completed, lastResult=${toolSet.lastResult != null}")
            val result = toolSet.lastResult
            if (result != null) {
                Result.success(result.copy(originalTask = request.taskTitle))
            } else {
                Result.failure(Exception("No subtask division result produced"))
            }
        } catch (e: Exception) {
            Log.e("AISubtaskDivider", "Unexpected error in task division", e)
            Result.failure(e)
        }
    }

    private fun buildUserPrompt(request: SubtaskDivisionRequest): String {
        val strategyHint = when (request.strategy) {
            DivisionStrategy.DETAILED -> "fine-grained, specific steps"
            DivisionStrategy.BALANCED -> "balanced detail and manageability"
            DivisionStrategy.SIMPLIFIED -> "high-level phases"
        }
        val priorityHint = when (request.taskPriority) {
            Priority.HIGH -> "high-priority, focus on critical path"
            Priority.MEDIUM -> "medium-priority, balance quality and speed"
            Priority.LOW -> "low-priority, focus on completeness"
            Priority.DEFAULT -> "normal priority"
        }
        return buildString {
            appendLine("Task: ${request.taskTitle}")
            request.taskContent?.let { appendLine("Description: $it") }
            appendLine("Priority: $priorityHint")
            appendLine("Max subtasks: ${request.maxSubtasks}")
            appendLine("Strategy: $strategyHint")
            request.context?.let { appendLine("Context: $it") }
        }.trim()
    }
}
