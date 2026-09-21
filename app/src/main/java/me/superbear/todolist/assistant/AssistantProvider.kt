package me.superbear.todolist.assistant

/**
 * App-level provider identity.
 *
 * [CUSTOM] is mapped to Koog's OpenAI provider internally because it speaks the
 * OpenAI Chat Completions protocol, but remains distinct in settings and persistence.
 */
enum class AssistantProvider(val id: String) {
    OPENAI("openai"),
    DEEPSEEK("deepseek"),
    CUSTOM("custom");

    companion object {
        fun fromId(id: String?): AssistantProvider =
            entries.firstOrNull { it.id == id } ?: OPENAI
    }
}
