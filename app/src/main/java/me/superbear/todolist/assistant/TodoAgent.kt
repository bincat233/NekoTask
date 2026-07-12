package me.superbear.todolist.assistant

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import io.ktor.client.HttpClient
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.data.repository.LongTermMemoryRepository
import me.superbear.todolist.domain.entities.ChatMessage
import me.superbear.todolist.domain.entities.Sender

class TodoAgent(
    private val repository: TodoRepository,
    private val memoryRepository: LongTermMemoryRepository
) : ChatAgent {
    private var openAIKey: String = ""
    private var deepSeekKey: String = ""
    private var selectedProvider: LLMProvider = LLMProvider.OpenAI
    private var selectedModel: LLModel = OpenAIModels.Chat.GPT4o

    private val taskToolSet = TaskToolSet(repository)
    private val memoryToolSet = MemoryToolSet(memoryRepository)

    private val baseSystemPrompt = """
You are a helpful assistant for a to-do list app.
Use tools to create, update, delete, or complete tasks.
When you use tools, keep a short user-facing reply.
If the request is ambiguous, ask a clarifying question and do not call tools.
You will receive CURRENT_TODO_STATE with task IDs, titles, statuses, and priorities.
Use those IDs when referencing existing tasks.

You also have long-term memory tools. When the user shares a lasting preference, habit, or
personal fact worth remembering across conversations, call add_memory. If MEMORY CONTEXT
already contains something that's now outdated or wrong, call update_memory or delete_memory
with its ID instead of adding a duplicate. Use list_memories if you need to look up IDs.
Don't save trivial or one-off details.
""".trimIndent()

    override fun buildExecutor(): PromptExecutor {
        val pairs = buildList {
            if (openAIKey.isNotBlank()) {
                add(
                    LLMProvider.OpenAI to OpenAILLMClient(
                        apiKey = openAIKey,
                        httpClientFactory = KtorKoogHttpClient.Factory(HttpClient())
                    )
                )
            }
            if (deepSeekKey.isNotBlank()) {
                add(
                    LLMProvider.DeepSeek to OpenAILLMClient(
                        apiKey = deepSeekKey,
                        settings = OpenAIClientSettings(baseUrl = "https://api.deepseek.com"),
                        httpClientFactory = KtorKoogHttpClient.Factory(HttpClient())
                    )
                )
            }
        }
        return MultiLLMPromptExecutor(*pairs.toTypedArray())
    }

    private fun buildAgent(language: String): AIAgent<String, String> {
        val langDirective = if (language.startsWith("zh")) "Please reply in Chinese." else "Please reply in English."
        return AIAgent(
            promptExecutor = buildExecutor(),
            systemPrompt = "$baseSystemPrompt\n$langDirective",
            llmModel = selectedModel,
            toolRegistry = ToolRegistry { tools(taskToolSet); tools(memoryToolSet) },
            maxIterations = 5
        )
    }

    override fun setApiKey(provider: LLMProvider, key: String) {
        when (provider) {
            LLMProvider.OpenAI -> openAIKey = key
            LLMProvider.DeepSeek -> deepSeekKey = key
            else -> {}
        }
    }

    override fun selectProvider(provider: LLMProvider) {
        selectedProvider = provider
        selectedModel = when (provider) {
            LLMProvider.OpenAI -> OpenAIModels.Chat.GPT4o
            LLMProvider.DeepSeek -> LLModel(provider = LLMProvider.DeepSeek, id = "deepseek-v4-flash")
            else -> selectedModel
        }
    }

    fun selectModel(model: LLModel) {
        selectedModel = model
    }

    override fun selectModelByName(name: String) {
        val known = when (selectedProvider) {
            LLMProvider.OpenAI -> listOf(
                LLModel(provider = LLMProvider.OpenAI, id = "gpt-5"),
                LLModel(provider = LLMProvider.OpenAI, id = "gpt-5-mini"),
                LLModel(provider = LLMProvider.OpenAI, id = "gpt-5-nano"),
                OpenAIModels.Chat.GPT4o,
                OpenAIModels.Chat.GPT4oMini,
                OpenAIModels.Chat.GPT4_1,
                OpenAIModels.Chat.GPT4_1Mini,
                OpenAIModels.Chat.GPT4_1Nano,
                OpenAIModels.Chat.O3Mini,
                OpenAIModels.Chat.O4Mini
            )
            LLMProvider.DeepSeek -> listOf(
                LLModel(provider = LLMProvider.DeepSeek, id = "deepseek-v4-flash"),
                LLModel(provider = LLMProvider.DeepSeek, id = "deepseek-v4-pro")
            )
            else -> emptyList()
        }
        selectedModel = known.firstOrNull { it.id == name }
            ?: LLModel(provider = selectedProvider, id = name)
    }

    override fun getCurrentModel(): LLModel = selectedModel

    fun getCurrentModelName(): String = selectedModel.id

    fun getCurrentProvider(): LLMProvider = selectedProvider

    override suspend fun chat(
        userMessage: String,
        history: List<ChatMessage>,
        todoState: String,
        memoryContext: String,
        language: String
    ): Result<String> {
        return try {
            val agent = buildAgent(language)

            val prompt = buildString {
                appendLine("CURRENT_TODO_STATE:")
                appendLine(todoState)
                if (memoryContext.isNotBlank()) {
                    appendLine()
                    appendLine("MEMORY CONTEXT:")
                    appendLine(memoryContext)
                }
                if (history.isNotEmpty()) {
                    appendLine()
                    appendLine("PREVIOUS CONVERSATION:")
                    history.takeLast(10).forEach { msg ->
                        val role = if (msg.sender == Sender.User) "User" else "Assistant"
                        appendLine("$role: ${msg.text}")
                    }
                }
                appendLine()
                appendLine("USER REQUEST: $userMessage")
            }

            val result = agent.run(prompt)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
