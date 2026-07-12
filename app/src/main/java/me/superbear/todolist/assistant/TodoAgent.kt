package me.superbear.todolist.assistant

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponse
import ai.koog.serialization.typeToken
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.data.repository.LongTermMemoryRepository
import me.superbear.todolist.domain.entities.ChatMessage
import me.superbear.todolist.domain.entities.Sender

class TodoAgent(
    private val repository: TodoRepository,
    private val memoryRepository: LongTermMemoryRepository
) : ChatAgent, LlmRuntime {

    companion object {
        // Koog ships no built-in model catalog for DeepSeek (it's routed through OpenAILLMClient
        // via a custom baseUrl), so capabilities must be declared by hand — otherwise
        // OpenAILLMClient.determineParams can't tell which param shape to use and throws.
        // Verified against https://api-docs.deepseek.com/api/create-chat-completion/ and the
        // tool_calls guide: chat-completions style endpoint (no OpenAI "Responses" endpoint, no
        // `n` param for multiple choices, no vision/document input), tools/tool_choice supported,
        // response_format supports basic {"type":"json_object"} only (no json_schema/strict mode
        // for general responses — only for tool-call arguments, which Koog has no capability for).
        private val DEEPSEEK_CAPABILITIES = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Completion,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.OpenAIEndpoint.Completions
        )
        val DeepSeekV4Flash = LLModel(provider = LLMProvider.DeepSeek, id = "deepseek-v4-flash", capabilities = DEEPSEEK_CAPABILITIES)
        val DeepSeekV4Pro = LLModel(provider = LLMProvider.DeepSeek, id = "deepseek-v4-pro", capabilities = DEEPSEEK_CAPABILITIES)
    }

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
For checklist, planning, packing-list, preparation-list, itinerary, or "N-item list" requests,
create one parent task with ordered, checkable subtasks by calling add_task_with_subtasks.
If the user asks to add a checklist to an existing task, call add_subtasks with that parent task ID.
Do not put multi-item checklists into a task's content field.
Use task content only for short notes that are not independently checkable.
For multiple independent, unrelated tasks in one request, call add_tasks once with all of them
instead of calling add_task repeatedly.
To clear a task's existing content or due date with update_task, pass the literal string "remove"
for that field; omitting the field leaves it unchanged.
CURRENT_TODO_STATE does not include task content/notes. Before appending to or referencing a
task's existing notes, call get_task first to see its current content instead of assuming or
overwriting it blindly.

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

    private fun buildAgent(
        language: String,
        streaming: Boolean = false,
        onEvent: (ChatStreamEvent) -> Unit = {}
    ): GraphAIAgent<String, String> {
        val langDirective = if (language.startsWith("zh")) "Please reply in Chinese." else "Please reply in English."
        val strategy = if (streaming) streamingSingleRunStrategy() else singleRunStrategy()

        return AIAgent(
            promptExecutor = buildExecutor(),
            llmModel = selectedModel,
            strategy = strategy,
            systemPrompt = "$baseSystemPrompt\n$langDirective",
            toolRegistry = ToolRegistry { tools(taskToolSet); tools(memoryToolSet) },
            maxIterations = 5,
            installFeatures = {
                if (streaming) {
                    install(ChatStreamingFeature) {
                        this.onEvent = onEvent
                    }
                }
            }
        )
    }

    private fun streamingSingleRunStrategy(): AIAgentGraphStrategy<String, String> = strategy("streaming_single_run") {
        val nodeCallLLM by node<String, Message.Assistant>("call_llm_streaming") { message ->
            llm.writeSession {
                appendPrompt { user(message) }
                val response = requestLLMStreaming().toList<StreamFrame>().toMessageResponse()
                appendPrompt { message(response) }
                response
            }
        }
        val nodeExecuteTool by nodeExecuteTools()
        val nodeSendToolResult by node<ReceivedToolResults, Message.Assistant>("send_tool_result_streaming") { toolResults ->
            llm.writeSession {
                appendPrompt {
                    user {
                        toolResults.toolResults.forEach { toolResult -> toolResult(toolResult.toMessagePart()) }
                    }
                }
                val response = requestLLMStreaming().toList<StreamFrame>().toMessageResponse()
                appendPrompt { message(response) }
                response
            }
        }

        edge(nodeStart forwardTo nodeCallLLM)
        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCalls { true })
        edge(nodeCallLLM forwardTo nodeFinish onTextMessage { true })
        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeFinish onTextMessage { true })
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
    }

    private object ChatStreamingFeature : AIAgentGraphFeature<ChatStreamingFeature.Config, Unit> {
        class Config(var onEvent: (ChatStreamEvent) -> Unit = {}) : FeatureConfig()
        override val key = AIAgentStorageKey<Unit>("ChatStreaming", typeToken<Unit>())
        override fun createInitialConfig(agentConfig: AIAgentConfig) = Config()

        override fun install(config: Config, pipeline: AIAgentGraphPipeline) {
            pipeline.interceptLLMStreamingFrameReceived(this) { ctx ->
                when (val frame = ctx.streamFrame) {
                    is StreamFrame.TextDelta -> config.onEvent(ChatStreamEvent.TextDelta(frame.text))
                    else -> Unit
                }
            }
            pipeline.interceptAgentCompleted(this) {
                config.onEvent(ChatStreamEvent.AgentCompleted)
            }
        }
    }

    override fun chatStreaming(
        userMessage: String,
        history: List<ChatMessage>,
        todoState: String,
        memoryContext: String,
        language: String
    ): Flow<ChatStreamEvent> = callbackFlow {
        val agent = buildAgent(language, streaming = true) { event ->
            trySend(event)
            if (event is ChatStreamEvent.AgentCompleted) {
                close()
            }
        }
        val prompt = buildChatPrompt(userMessage, history, todoState, memoryContext)

        val job = launch {
            try {
                agent.run(prompt, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trySend(ChatStreamEvent.Error(e))
                close()
            }
        }

        awaitClose {
            job.cancel()
            runBlocking { agent.close() }
        }
    }

    private fun buildChatPrompt(
        userMessage: String,
        history: List<ChatMessage>,
        todoState: String,
        memoryContext: String
    ): String = buildString {
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
            LLMProvider.DeepSeek -> DeepSeekV4Flash
            else -> selectedModel
        }
    }

    fun selectModel(model: LLModel) {
        selectedModel = model
    }

    override fun selectModelByName(name: String) {
        val known = when (selectedProvider) {
            LLMProvider.OpenAI -> listOf(
                OpenAIModels.Chat.GPT5_5,
                OpenAIModels.Chat.GPT5_4,
                OpenAIModels.Chat.GPT5_4Mini,
                OpenAIModels.Chat.GPT5_4Nano,
                OpenAIModels.Chat.GPT5_2,
                OpenAIModels.Chat.GPT5_1,
                OpenAIModels.Chat.GPT5,
                OpenAIModels.Chat.GPT5Mini,
                OpenAIModels.Chat.GPT5Nano,
                OpenAIModels.Chat.GPT4o,
                OpenAIModels.Chat.GPT4oMini,
                OpenAIModels.Chat.GPT4_1,
                OpenAIModels.Chat.GPT4_1Mini,
                OpenAIModels.Chat.GPT4_1Nano,
                OpenAIModels.Chat.O3Mini,
                OpenAIModels.Chat.O4Mini
            )
            LLMProvider.DeepSeek -> listOf(
                DeepSeekV4Flash,
                DeepSeekV4Pro
            )
            else -> emptyList()
        }
        selectedModel = known.firstOrNull { it.id == name }
            ?: LLModel(provider = selectedProvider, id = name)
    }

    override fun getCurrentModel(): LLModel = selectedModel

    override suspend fun chat(
        userMessage: String,
        history: List<ChatMessage>,
        todoState: String,
        memoryContext: String,
        language: String
    ): Result<String> {
        return try {
            val agent = buildAgent(language)
            val prompt = buildChatPrompt(userMessage, history, todoState, memoryContext)
            val result = agent.run(prompt, null)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
