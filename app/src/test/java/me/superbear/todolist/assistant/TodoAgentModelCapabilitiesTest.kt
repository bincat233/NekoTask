package me.superbear.todolist.assistant

import ai.koog.prompt.llm.LLMCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins TodoAgent's hand-declared DeepSeek [LLModel] capabilities to what
 * https://api-docs.deepseek.com actually documents, so a future edit can't silently
 * reintroduce a capability DeepSeek doesn't support (or drop the one capability that
 * prevents OpenAILLMClient.determineParams from throwing "Cannot determine proper LLM
 * params").
 */
class TodoAgentModelCapabilitiesTest {

    @Test
    fun deepSeekModelIdsMatchProviderApi() {
        assertEquals("deepseek-v4-flash", TodoAgent.DeepSeekV4Flash.id)
        assertEquals("deepseek-v4-pro", TodoAgent.DeepSeekV4Pro.id)
    }

    @Test
    fun deepSeekModelsDeclareTheCapabilityDetermineParamsRequires() {
        // Without this, OpenAILLMClient.determineParams has no way to know DeepSeek's requests
        // should be shaped as classic chat-completions params, and throws.
        assertTrue(TodoAgent.DeepSeekV4Flash.supports(LLMCapability.OpenAIEndpoint.Completions))
        assertTrue(TodoAgent.DeepSeekV4Pro.supports(LLMCapability.OpenAIEndpoint.Completions))
    }

    @Test
    fun customModelsUseOpenAIChatCompletionsWithToolCalling() {
        val model = TodoAgent.customOpenAIModel("local-model")

        assertEquals("local-model", model.id)
        assertTrue(model.supports(LLMCapability.OpenAIEndpoint.Completions))
        assertFalse(model.supports(LLMCapability.OpenAIEndpoint.Responses))
        assertTrue(model.supports(LLMCapability.Tools))
        assertTrue(model.supports(LLMCapability.ToolChoice))
    }

    @Test
    fun customBaseUrlAcceptsOptionalV1Suffix() {
        val withoutV1 = TodoAgent.customOpenAISettings("https://example.com/")
        val withV1 = TodoAgent.customOpenAISettings("https://example.com/v1/")

        assertEquals("https://example.com", withoutV1.baseUrl)
        assertEquals("v1/chat/completions", withoutV1.chatCompletionsPath)
        assertEquals("https://example.com", withV1.baseUrl)
        assertEquals("v1/chat/completions", withV1.chatCompletionsPath)
    }

    @Test
    fun ollamaBaseUrlWithV1ResolvesToOpenAICompatibleEndpoint() {
        val settings = TodoAgent.customOpenAISettings("http://192.168.2.26:11434/v1")

        assertEquals("http://192.168.2.26:11434", settings.baseUrl)
        assertEquals("v1/chat/completions", settings.chatCompletionsPath)
    }

    @Test
    fun deepSeekModelsDoNotClaimTheResponsesEndpoint() {
        // DeepSeek only implements the classic chat-completions endpoint, not OpenAI's
        // proprietary Responses API.
        assertFalse(TodoAgent.DeepSeekV4Flash.supports(LLMCapability.OpenAIEndpoint.Responses))
        assertFalse(TodoAgent.DeepSeekV4Pro.supports(LLMCapability.OpenAIEndpoint.Responses))
    }

    @Test
    fun deepSeekModelsDeclareToolCallingSupport() {
        assertTrue(TodoAgent.DeepSeekV4Flash.supports(LLMCapability.Tools))
        assertTrue(TodoAgent.DeepSeekV4Flash.supports(LLMCapability.ToolChoice))
    }

    @Test
    fun deepSeekModelsDeclareBasicJsonObjectModeOnly() {
        // response_format only supports {"type": "json_object"}, not a json_schema/strict mode
        // for general responses.
        assertTrue(TodoAgent.DeepSeekV4Flash.supports(LLMCapability.Schema.JSON.Basic))
        assertFalse(TodoAgent.DeepSeekV4Flash.supports(LLMCapability.Schema.JSON.Standard))
    }

    @Test
    fun deepSeekModelsDoNotClaimUnverifiedCapabilities() {
        // No `n` param in the docs (no multiple choices), no vision/document input mentioned.
        assertFalse(TodoAgent.DeepSeekV4Flash.supports(LLMCapability.MultipleChoices))
        assertFalse(TodoAgent.DeepSeekV4Flash.supports(LLMCapability.Vision.Image))
        assertFalse(TodoAgent.DeepSeekV4Flash.supports(LLMCapability.Document))
    }
}
