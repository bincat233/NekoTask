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
