package me.superbear.todolist.assistant.subtask

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.superbear.todolist.domain.entities.Priority

class AISubtaskDivider(
    private val promptExecutor: PromptExecutor,
    private val model: LLModel
) : SubtaskDivider {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val toolSet = DivideTaskToolSet()

    override suspend fun divideTask(request: SubtaskDivisionRequest): Result<SubtaskDivisionResponse> {
        val systemPrompt = "You are a task decomposition assistant. Break the given task into concrete, executable subtasks."
        val userPrompt = buildUserPrompt(request)

        return try {
            val agentConfig = AIAgentConfig(
                prompt = prompt(
                    id = "divide_task",
                    params = LLMParams(toolChoice = LLMParams.ToolChoice.Required)
                ) {
                    system(systemPrompt)
                },
                model = model,
                maxAgentIterations = 1
            )

            val agent = AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = agentConfig,
                toolRegistry = ToolRegistry { tools(toolSet) }
            )

            agent.run(userPrompt)
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

    @LLMDescription("Produces a task decomposition with ordered subtasks")
    inner class DivideTaskToolSet : ToolSet {
        var lastResult: SubtaskDivisionResponse? = null

        @Tool
        @LLMDescription("Divide a task into ordered, executable subtasks")
        fun divide_task(
            @LLMDescription("Brief decomposition rationale") reasoning: String,
            @LLMDescription("JSON array of subtask objects with keys: title, content, priority, estimatedOrder, dependencies")
            subtasks: String
        ): String {
            val dtoList = try {
                json.decodeFromString<List<SubtaskDto>>(subtasks)
            } catch (e: Exception) {
                Log.e("AISubtaskDivider", "Failed to parse subtasks JSON: $subtasks", e)
                return "Error: invalid subtasks format"
            }
            val suggestions = dtoList.mapIndexed { index, dto ->
                SubtaskSuggestion(
                    title = dto.title,
                    content = dto.content,
                    priority = parsePriority(dto.priority),
                    estimatedOrder = dto.estimatedOrder ?: (index + 1),
                    dependencies = dto.dependencies ?: emptyList()
                )
            }
            lastResult = SubtaskDivisionResponse(
                originalTask = "",
                subtasks = suggestions,
                reasoning = reasoning
            )
            return "ok"
        }
    }

    private fun parsePriority(priorityStr: String?): Priority {
        return when (priorityStr?.uppercase()) {
            "HIGH" -> Priority.HIGH
            "MEDIUM" -> Priority.MEDIUM
            "LOW" -> Priority.LOW
            else -> Priority.DEFAULT
        }
    }

    @Serializable
    private data class SubtaskDto(
        val title: String,
        val content: String? = null,
        val priority: String? = null,
        @SerialName("estimatedOrder")
        val estimatedOrder: Int? = null,
        val dependencies: List<Int>? = null
    )
}
