package me.superbear.todolist.assistant

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import me.superbear.todolist.data.repository.LongTermMemoryRepository

@LLMDescription("Tools for managing the AI assistant's long-term memory about the user")
class MemoryToolSet(
    private val repository: LongTermMemoryRepository
) : ToolSet {

    @Tool
    @LLMDescription("Save a new long-term memory about the user's preferences, habits, or context, to recall in future conversations")
    suspend fun add_memory(
        @LLMDescription("The fact or preference to remember, written as a short standalone statement") content: String,
        @LLMDescription("Category: general, preferences, work_habits, project_info, personal, or context") category: String? = null,
        @LLMDescription("Importance 1-5, 5 being most important") importance: Int? = null
    ): String {
        repository.createMemory(
            content = content.trim(),
            category = mapCategory(category),
            importance = importance?.coerceIn(1, 5) ?: 3
        )
        return "ok"
    }

    @Tool
    @LLMDescription("Update an existing long-term memory by its ID")
    suspend fun update_memory(
        @LLMDescription("Memory ID to update") id: Long,
        @LLMDescription("New content") content: String,
        @LLMDescription("New category: general, preferences, work_habits, project_info, personal, or context") category: String? = null,
        @LLMDescription("New importance 1-5") importance: Int? = null
    ): String {
        repository.updateMemory(
            id = id,
            content = content.trim(),
            category = category?.let { mapCategory(it) },
            importance = importance?.coerceIn(1, 5)
        )
        return "ok"
    }

    @Tool
    @LLMDescription("Delete a long-term memory by its ID when it's no longer relevant or accurate")
    suspend fun delete_memory(
        @LLMDescription("Memory ID to delete") id: Long
    ): String {
        repository.deleteMemory(id)
        return "ok"
    }

    @Tool
    @LLMDescription("List all currently active long-term memories with their IDs, so you can reference them for update or delete")
    suspend fun list_memories(): String {
        val memories = repository.getActiveMemoriesSnapshot()
        if (memories.isEmpty()) return "No memories stored yet."
        return memories.joinToString("\n") { memory ->
            "${memory.id}: [${memory.category}] ${memory.content} (importance ${memory.importance})"
        }
    }

    companion object {
        private val KNOWN_CATEGORIES = setOf(
            "general", "preferences", "work_habits", "project_info", "personal", "context"
        )

        fun mapCategory(category: String?): String =
            category?.lowercase()?.takeIf { it in KNOWN_CATEGORIES } ?: "general"
    }
}
