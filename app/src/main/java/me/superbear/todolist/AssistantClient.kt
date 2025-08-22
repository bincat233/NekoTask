package me.superbear.todolist

import kotlinx.coroutines.delay
import kotlin.random.Random

interface AssistantClient {
    suspend fun send(userText: String, history: List<ChatMessage>): Result<String>
}

class MockAssistantClient : AssistantClient {
    override suspend fun send(userText: String, history: List<ChatMessage>): Result<String> {
        delay(Random.nextLong(1000, 5000))
        return Result.success(userText) // echo
    }
}
