package me.superbear.todolist.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import me.superbear.todolist.data.dao.LongTermMemoryDao
import me.superbear.todolist.domain.entities.LongTermMemory

/**
 * 长期记忆仓库
 */
class LongTermMemoryRepository(
    private val memoryDao: LongTermMemoryDao
) {
    
    /**
     * 获取所有激活的记忆
     */
    fun getActiveMemories(): Flow<List<LongTermMemory>> {
        return memoryDao.getActiveMemories()
    }
    
    /**
     * 获取所有记忆（用于设置页面）
     */
    fun getAllMemories(): Flow<List<LongTermMemory>> {
        return memoryDao.getAllMemories()
    }
    
    /**
     * 根据类别获取记忆
     */
    fun getMemoriesByCategory(category: String): Flow<List<LongTermMemory>> {
        return memoryDao.getMemoriesByCategory(category)
    }
    
    /**
     * 创建新记忆
     */
    suspend fun createMemory(
        content: String,
        category: String = "general",
        importance: Int = 3,
        isActive: Boolean = true
    ): Long {
        val now = Clock.System.now()
        val memory = LongTermMemory(
            content = content,
            category = category,
            importance = importance,
            isActive = isActive,
            createdAt = now,
            updatedAt = now
        )
        return memoryDao.insertMemory(memory)
    }
    
    /**
     * 更新记忆内容
     */
    suspend fun updateMemory(
        id: Long,
        content: String,
        category: String? = null,
        importance: Int? = null,
        isActive: Boolean? = null
    ) {
        val existingMemory = memoryDao.getMemoryById(id) ?: return
        val updatedMemory = existingMemory.copy(
            content = content,
            category = category ?: existingMemory.category,
            importance = importance ?: existingMemory.importance,
            isActive = isActive ?: existingMemory.isActive,
            updatedAt = Clock.System.now()
        )
        memoryDao.updateMemory(updatedMemory)
    }
    
    /**
     * 删除记忆
     */
    suspend fun deleteMemory(id: Long) {
        val memory = memoryDao.getMemoryById(id) ?: return
        memoryDao.deleteMemory(memory)
    }
    
    /**
     * 切换记忆激活状态
     */
    suspend fun toggleMemoryActive(id: Long, isActive: Boolean) {
        memoryDao.toggleMemoryActive(id, isActive)
    }
    
    /**
     * 更新记忆的最后使用时间（当AI使用该记忆时调用）
     */
    suspend fun markMemoryUsed(id: Long) {
        memoryDao.updateLastUsedAt(id, Clock.System.now().epochSeconds)
    }
    
    /**
     * 获取用于AI对话的记忆上下文
     */
    suspend fun getMemoryContextForAI(limit: Int = 10): String {
        val memories = memoryDao.getTopActiveMemories(limit)
        if (memories.isEmpty()) {
            return ""
        }
        
        val contextBuilder = StringBuilder()
        contextBuilder.append("=== AI Assistant Long-term Memory ===\n")
        contextBuilder.append("The following are important memories that should be considered in our conversation:\n\n")
        
        memories.forEachIndexed { index, memory ->
            contextBuilder.append("${index + 1}. [${memory.category}] ${memory.content}\n")
        }
        
        contextBuilder.append("\nPlease use this information to provide more personalized and contextually relevant responses.\n")
        contextBuilder.append("=== End of Memory Context ===\n\n")
        
        return contextBuilder.toString()
    }
    
    /**
     * 批量删除记忆
     */
    suspend fun deleteMemories(ids: List<Long>) {
        memoryDao.deleteMemoriesByIds(ids)
    }
}
