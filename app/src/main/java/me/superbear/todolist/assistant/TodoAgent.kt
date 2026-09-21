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
import android.util.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.superbear.todolist.assistant.search.SearchCapabilityKind
import me.superbear.todolist.assistant.search.TavilySearchProvider
import me.superbear.todolist.data.TodoRepository
import me.superbear.todolist.data.repository.LongTermMemoryRepository
import me.superbear.todolist.domain.entities.ChatMessage
import me.superbear.todolist.domain.entities.Sender

class TodoAgent(
    private val repository: TodoRepository,
    private val memoryRepository: LongTermMemoryRepository
) : ChatAgent, LlmRuntime, SearchRuntime {

    companion object {
        private const val TAG = "TodoAgent"
        // Koog ships no built-in model catalog for DeepSeek (it's routed through OpenAILLMClient
        // via a custom baseUrl), so capabilities must be declared by hand — otherwise
        // OpenAILLMClient.determineParams can't tell which param shape to use and throws.
        // Verified against https://api-docs.deepseek.com/api/create-chat-completion/ and the
        // tool_calls guide: chat-completions style endpoint (no OpenAI "Responses" endpoint, no
        // `n` param for multiple choices, no vision/document input), tools/tool_choice supported,
        // response_format supports basic {"type":"json_object"} only (no json_schema/strict mode
        // for general responses — only for tool-call arguments, which Koog has no capability for).
        private val CHAT_COMPLETIONS_CAPABILITIES = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Completion,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.OpenAIEndpoint.Completions
        )
        val DeepSeekV4Flash = LLModel(provider = LLMProvider.DeepSeek, id = "deepseek-v4-flash", capabilities = CHAT_COMPLETIONS_CAPABILITIES)
        val DeepSeekV4Pro = LLModel(provider = LLMProvider.DeepSeek, id = "deepseek-v4-pro", capabilities = CHAT_COMPLETIONS_CAPABILITIES)

        fun customOpenAIModel(id: String): LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = id,
            capabilities = CHAT_COMPLETIONS_CAPABILITIES
        )

        fun customOpenAISettings(baseUrl: String): OpenAIClientSettings {
            val trimmed = baseUrl.trim().trimEnd('/')
            // Ktor resolves "chat/completions" against a base ending in /v1 as a sibling path,
            // producing /chat/completions. Normalize the optional suffix away and always append
            // Koog's full v1 path so both https://host and https://host/v1 resolve identically.
            val normalized = trimmed.removeSuffix("/v1").removeSuffix("/V1")
            return OpenAIClientSettings(
                baseUrl = normalized,
                chatCompletionsPath = "v1/chat/completions"
            )
        }
    }

    private var openAIKey: String = ""
    private var deepSeekKey: String = ""
    private var customKey: String = ""
    private var customBaseUrl: String = ""
    private var selectedProvider: AssistantProvider = AssistantProvider.OPENAI
    private var selectedModel: LLModel = OpenAIModels.Chat.GPT4o

    private val taskToolSet = TaskToolSet(repository)
    private val memoryToolSet = MemoryToolSet(memoryRepository)

    // Only one ExternalApi provider exists today (Tavily), so key updates always target this
    // instance directly. If a second provider is added, setSearchApiKey will need to become
    // provider-aware (e.g. keyed by which provider is currently selected) — not needed yet.
    private val tavilyProvider = TavilySearchProvider()
    private var searchCapabilityKind: SearchCapabilityKind = SearchCapabilityKind.TAVILY

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
        val pairs = when (selectedProvider) {
            AssistantProvider.OPENAI -> buildList {
                if (openAIKey.isNotBlank()) add(
                    LLMProvider.OpenAI to OpenAILLMClient(
                        apiKey = openAIKey,
                        httpClientFactory = KtorKoogHttpClient.Factory(HttpClient())
                    )
                )
            }
            AssistantProvider.DEEPSEEK -> buildList {
                if (deepSeekKey.isNotBlank()) add(
                    LLMProvider.DeepSeek to OpenAILLMClient(
                        apiKey = deepSeekKey,
                        settings = OpenAIClientSettings(baseUrl = "https://api.deepseek.com"),
                        httpClientFactory = KtorKoogHttpClient.Factory(HttpClient())
                    )
                )
            }
            AssistantProvider.CUSTOM -> buildList {
                if (customBaseUrl.isNotBlank()) add(
                    LLMProvider.OpenAI to OpenAILLMClient(
                        apiKey = customKey,
                        settings = customOpenAISettings(customBaseUrl),
                        httpClientFactory = KtorKoogHttpClient.Factory(HttpClient())
                    )
                )
            }
        }
        val endpoint = if (selectedProvider == AssistantProvider.CUSTOM) {
            customOpenAISettings(customBaseUrl).let { "${it.baseUrl}/${it.chatCompletionsPath}" }
        } else {
            "provider-default"
        }
        val keyConfigured = when (selectedProvider) {
            AssistantProvider.OPENAI -> openAIKey.isNotBlank()
            AssistantProvider.DEEPSEEK -> deepSeekKey.isNotBlank()
            AssistantProvider.CUSTOM -> customKey.isNotBlank()
        }
        Log.d(
            TAG,
            "buildExecutor provider=$selectedProvider model=${selectedModel.id} endpoint=$endpoint " +
                "apiKeyConfigured=$keyConfigured clients=${pairs.size}"
        )
        return MultiLLMPromptExecutor(*pairs.toTypedArray())
    }

    private fun buildAgent(
        language: String,
        streaming: Boolean = false,
        onEvent: (ChatStreamEvent) -> Unit = {}
    ): GraphAIAgent<String, String> {
        val langDirective = if (language.startsWith("zh")) "Please reply in Chinese." else "Please reply in English."
        val strategy = if (streaming) streamingSingleRunStrategy() else singleRunStrategy()

        val searchToolSet = when (searchCapabilityKind) {
            SearchCapabilityKind.TAVILY -> SearchToolSet(tavilyProvider)
        }
        val searchGuidance = "\nYou also have a web_search tool for looking up current information the user asks " +
            "about (e.g. facts, news, prices) that isn't already in CURRENT_TODO_STATE or memory " +
            "context. Use it when the user's request needs real-world/current information; " +
            "don't reach for it while just managing tasks."

        return AIAgent(
            promptExecutor = buildExecutor(),
            llmModel = selectedModel,
            strategy = strategy,
            systemPrompt = "$baseSystemPrompt$searchGuidance\n$langDirective",
            toolRegistry = ToolRegistry {
                tools(taskToolSet)
                tools(memoryToolSet)
                tools(searchToolSet)
            },
            maxIterations = 5,
            installFeatures = {
                install(AgentTraceFeature) {}
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

    // Structured per-node/per-tool-call trace logging, so `chat()` and `chatStreaming()` (which
    // run different strategy graphs) both get visibility into intermediate agent output — the
    // observability trajectory eval needs, not just the final response.
    private object AgentTraceFeature : AIAgentGraphFeature<AgentTraceFeature.Config, Unit> {
        class Config : FeatureConfig()
        override val key = AIAgentStorageKey<Unit>("AgentTrace", typeToken<Unit>())
        override fun createInitialConfig(agentConfig: AIAgentConfig) = Config()

        private const val TAG = "AgentTrace"
        private const val MAX_LEN = 500

        // Framework passthrough nodes (__start__/__finish__) just echo the adjacent real node's
        // input/output verbatim, and *ExecuteTool node dumps duplicate the tool=... CALL/RESULT
        // lines below but with a long static toolDescription field that eats the whole truncation
        // budget before reaching the actually-interesting output/result fields. Skip all three at
        // the node level; the tool-call intercepts already cover them more usefully.
        private fun isNoisyNode(name: String): Boolean =
            name == "__start__" || name == "__finish__" || name.contains("ExecuteTool", ignoreCase = true)

        private val META_INFO_RE = Regex(
            """metaInfo=ResponseMetaInfo\(timestamp=[^,]+, totalTokensCount=\d+, """ +
                """inputTokensCount=(\d+), outputTokensCount=(\d+), modelId=[^,]*, metadata=[^)]*\)"""
        )

        private fun Any?.summarize(): String {
            var s = this?.toString() ?: return "null"
            s = META_INFO_RE.replace(s, "tokens=in:$1/out:$2")
            s = s.replace(", rawResponse=null, id=null)", ")")
            s = s.replace(", cacheControl=null", "")
            return if (s.length > MAX_LEN) "${s.take(MAX_LEN)}…(${s.length} chars)" else s
        }

        override fun install(config: Config, pipeline: AIAgentGraphPipeline) {
            pipeline.interceptNodeExecutionStarting(this) { ctx ->
                if (isNoisyNode(ctx.node.name)) return@interceptNodeExecutionStarting
                android.util.Log.d(TAG, "node=${ctx.node.name} START input=${ctx.input.summarize()}")
            }
            pipeline.interceptNodeExecutionCompleted(this) { ctx ->
                if (isNoisyNode(ctx.node.name)) return@interceptNodeExecutionCompleted
                android.util.Log.d(TAG, "node=${ctx.node.name} DONE output=${ctx.output.summarize()}")
            }
            pipeline.interceptNodeExecutionFailed(this) { ctx ->
                android.util.Log.e(TAG, "node=${ctx.node.name} FAILED: ${ctx.error.message}", ctx.error)
            }
            pipeline.interceptToolCallStarting(this) { ctx ->
                android.util.Log.d(TAG, "tool=${ctx.toolName} CALL args=${ctx.toolArgs.summarize()}")
            }
            pipeline.interceptToolCallCompleted(this) { ctx ->
                android.util.Log.d(TAG, "tool=${ctx.toolName} RESULT=${ctx.toolResult.summarize()}")
            }
            pipeline.interceptToolCallFailed(this) { ctx ->
                android.util.Log.e(TAG, "tool=${ctx.toolName} FAILED: ${ctx.message}", ctx.error)
            }
        }
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
                Log.e(
                    TAG,
                    "chatStreaming failed provider=$selectedProvider model=${selectedModel.id} " +
                        "causeChain=${e.causeChain()}",
                    e
                )
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

    override fun setApiKey(provider: AssistantProvider, key: String) {
        when (provider) {
            AssistantProvider.OPENAI -> openAIKey = key
            AssistantProvider.DEEPSEEK -> deepSeekKey = key
            AssistantProvider.CUSTOM -> customKey = key
        }
    }

    override fun setCustomBaseUrl(baseUrl: String) {
        customBaseUrl = baseUrl.trim()
    }

    override fun selectProvider(provider: AssistantProvider) {
        selectedProvider = provider
        selectedModel = when (provider) {
            AssistantProvider.OPENAI -> OpenAIModels.Chat.GPT4o
            AssistantProvider.DEEPSEEK -> DeepSeekV4Flash
            AssistantProvider.CUSTOM -> customOpenAIModel("gpt-4o-mini")
        }
    }

    fun selectModel(model: LLModel) {
        selectedModel = model
    }

    override fun selectModelByName(name: String) {
        val known = when (selectedProvider) {
            AssistantProvider.OPENAI -> listOf(
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
            AssistantProvider.DEEPSEEK -> listOf(
                DeepSeekV4Flash,
                DeepSeekV4Pro
            )
            AssistantProvider.CUSTOM -> emptyList()
        }
        selectedModel = known.firstOrNull { it.id == name }
            ?: when (selectedProvider) {
                AssistantProvider.OPENAI -> LLModel(provider = LLMProvider.OpenAI, id = name)
                AssistantProvider.DEEPSEEK -> LLModel(provider = LLMProvider.DeepSeek, id = name)
                AssistantProvider.CUSTOM -> customOpenAIModel(name)
            }
    }

    override fun getCurrentModel(): LLModel = selectedModel

    override fun setSearchCapability(kind: SearchCapabilityKind) {
        searchCapabilityKind = kind
    }

    override fun setSearchApiKey(key: String) {
        tavilyProvider.setApiKey(key)
    }

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
            Log.e(
                TAG,
                "chat failed provider=$selectedProvider model=${selectedModel.id} " +
                    "causeChain=${e.causeChain()}",
                e
            )
            Result.failure(e)
        }
    }

    private fun Throwable.causeChain(): String = generateSequence(this) { it.cause }
        .take(6)
        .joinToString(" <- ") { cause ->
            "${cause.javaClass.simpleName}: ${cause.message ?: "(no message)"}"
        }
}
