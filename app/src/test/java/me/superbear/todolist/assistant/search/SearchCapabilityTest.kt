package me.superbear.todolist.assistant.search

import ai.koog.prompt.llm.LLMProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCapabilityTest {

    @Test
    fun tavilyIsAvailableRegardlessOfLlmProvider() {
        assertTrue(SearchCapabilityKind.TAVILY.isAvailable(LLMProvider.OpenAI))
        assertTrue(SearchCapabilityKind.TAVILY.isAvailable(LLMProvider.DeepSeek))
    }

    @Test
    fun openAiNativeSearchIsOnlyAvailableForOpenAi() {
        assertTrue(SearchCapabilityKind.OPENAI_NATIVE.isAvailable(LLMProvider.OpenAI))
        assertFalse(SearchCapabilityKind.OPENAI_NATIVE.isAvailable(LLMProvider.DeepSeek))
    }
}
