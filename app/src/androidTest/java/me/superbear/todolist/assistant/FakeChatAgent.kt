package me.superbear.todolist.assistant

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import me.superbear.todolist.domain.entities.ChatMessage

/**
 * Deterministic [ChatAgent] double for Compose UI tests, so tests never hit a real LLM.
 * Records the last request it received for assertions.
 */
class FakeChatAgent(
    private val reply: String = "Fake AI reply"
) : ChatAgent {

    var lastUserMessage: String? = null
        private set
    var lastMemoryContext: String? = null
        private set
    var callCount: Int = 0
        private set

    override fun setApiKey(provider: LLMProvider, key: String) {}

    override fun selectProvider(provider: LLMProvider) {}

    override fun selectModelByName(name: String) {}

    override fun buildExecutor(): PromptExecutor = MultiLLMPromptExecutor()

    override fun getCurrentModel(): LLModel = LLModel(provider = LLMProvider.OpenAI, id = "fake-model")

    override suspend fun chat(
        userMessage: String,
        history: List<ChatMessage>,
        todoState: String,
        memoryContext: String,
        language: String
    ): Result<String> {
        callCount++
        lastUserMessage = userMessage
        lastMemoryContext = memoryContext
        return Result.success(reply)
    }
}
