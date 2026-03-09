package me.superbear.todolist.assistant.subtask

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.superbear.todolist.assistant.TextAssistantClient
import me.superbear.todolist.domain.entities.Priority

/**
 * AI驱动的子任务划分器
 */
class AISubtaskDivider(
    private val assistantClient: TextAssistantClient
) : SubtaskDivider {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    override suspend fun divideTask(request: SubtaskDivisionRequest): Result<SubtaskDivisionResponse> {
        val prompt = buildPrompt(request)
        
        return try {
            val response = assistantClient.sendText(prompt)
            response.fold(
                onSuccess = { responseText ->
                    parseAIResponse(responseText, request.taskTitle)
                },
                onFailure = { error ->
                    Log.e("AISubtaskDivider", "AI request failed", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e("AISubtaskDivider", "Unexpected error in task division", e)
            Result.failure(e)
        }
    }

    private fun buildPrompt(request: SubtaskDivisionRequest): String {
        val strategyDescription = when (request.strategy) {
            DivisionStrategy.DETAILED -> "生成详细的、细粒度的子任务，每个子任务都应该是具体可执行的小步骤"
            DivisionStrategy.BALANCED -> "生成适中数量的子任务，平衡详细程度和可管理性"
            DivisionStrategy.SIMPLIFIED -> "生成少量的高层次子任务，每个子任务覆盖较大的工作范围"
        }

        val priorityDescription = when (request.taskPriority) {
            Priority.HIGH -> "这是高优先级任务，子任务应该注重效率和关键路径"
            Priority.MEDIUM -> "这是中等优先级任务，子任务应该平衡质量和效率"
            Priority.LOW -> "这是低优先级任务，子任务可以更注重质量和完整性"
            Priority.DEFAULT -> "这是普通任务"
        }

        return """
请将以下任务分解为子任务：

任务标题：${request.taskTitle}
任务描述：${request.taskContent ?: "无"}
优先级：${priorityDescription}
最大子任务数：${request.maxSubtasks}
划分策略：${strategyDescription}
${request.context?.let { "额外上下文：$it" } ?: ""}

请返回一个JSON对象，格式如下：
{
  "reasoning": "分解思路的简要说明",
  "subtasks": [
    {
      "title": "子任务标题",
      "content": "子任务详细描述（可选）",
      "priority": "HIGH|MEDIUM|LOW|DEFAULT",
      "estimatedOrder": 1,
      "dependencies": [0, 1]
    }
  ]
}

要求：
1. 子任务应该具体、可执行、有明确的完成标准
2. 合理设置子任务的优先级和执行顺序
3. 如果子任务之间有依赖关系，请在dependencies中标明（使用数组索引）
4. 子任务标题要简洁明确，描述要具体实用
5. 确保所有子任务加起来能完整覆盖原任务的目标
"""
    }

    private fun parseAIResponse(responseText: String, originalTask: String): Result<SubtaskDivisionResponse> {
        return try {
            val jsonText = extractJson(responseText) ?: responseText
            val aiResponse = json.decodeFromString<AISubtaskResponse>(jsonText)
            
            val subtasks = aiResponse.subtasks.mapIndexed { index, dto ->
                SubtaskSuggestion(
                    title = dto.title,
                    content = dto.content,
                    priority = parsePriority(dto.priority),
                    estimatedOrder = dto.estimatedOrder ?: (index + 1),
                    dependencies = dto.dependencies ?: emptyList()
                )
            }
            
            Result.success(
                SubtaskDivisionResponse(
                    originalTask = originalTask,
                    subtasks = subtasks,
                    reasoning = aiResponse.reasoning
                )
            )
        } catch (e: Exception) {
            Log.e("AISubtaskDivider", "Failed to parse AI response: $responseText", e)
            Result.failure(Exception("解析AI响应失败: ${e.message}"))
        }
    }

    private fun extractJson(text: String): String? {
        val first = text.indexOf('{')
        val last = text.lastIndexOf('}')
        return if (first != -1 && last != -1 && first < last) {
            text.substring(first, last + 1)
        } else null
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
    private data class AISubtaskResponse(
        val reasoning: String? = null,
        val subtasks: List<AISubtaskDto>
    )

    @Serializable
    private data class AISubtaskDto(
        val title: String,
        val content: String? = null,
        val priority: String? = null,
        @SerialName("estimatedOrder")
        val estimatedOrder: Int? = null,
        val dependencies: List<Int>? = null
    )
}
