package me.superbear.todolist.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.superbear.todolist.domain.entities.LongTermMemory
import kotlinx.datetime.Instant

/**
 * 长期记忆数据访问对象
 */
@Dao
interface LongTermMemoryDao {
    
    /**
     * 获取所有激活的记忆，按重要性和最后使用时间排序
     */
    @Query("""
        SELECT * FROM long_term_memories 
        WHERE isActive = 1 
        ORDER BY importance DESC, lastUsedAt DESC, createdAt DESC
    """)
    fun getActiveMemories(): Flow<List<LongTermMemory>>
    
    /**
     * 获取所有记忆（用于设置页面编辑）
     */
    @Query("SELECT * FROM long_term_memories ORDER BY importance DESC, createdAt DESC")
    fun getAllMemories(): Flow<List<LongTermMemory>>
    
    /**
     * 根据类别获取记忆
     */
    @Query("SELECT * FROM long_term_memories WHERE category = :category AND isActive = 1")
    fun getMemoriesByCategory(category: String): Flow<List<LongTermMemory>>
    
    /**
     * 根据ID获取记忆
     */
    @Query("SELECT * FROM long_term_memories WHERE id = :id")
    suspend fun getMemoryById(id: Long): LongTermMemory?
    
    /**
     * 插入新记忆
     */
    @Insert
    suspend fun insertMemory(memory: LongTermMemory): Long
    
    /**
     * 更新记忆
     */
    @Update
    suspend fun updateMemory(memory: LongTermMemory)
    
    /**
     * 删除记忆
     */
    @Delete
    suspend fun deleteMemory(memory: LongTermMemory)
    
    /**
     * 更新记忆的最后使用时间
     */
    @Query("UPDATE long_term_memories SET lastUsedAt = :lastUsedAt WHERE id = :id")
    suspend fun updateLastUsedAt(id: Long, lastUsedAt: Long)
    
    /**
     * 切换记忆的激活状态
     */
    @Query("UPDATE long_term_memories SET isActive = :isActive WHERE id = :id")
    suspend fun toggleMemoryActive(id: Long, isActive: Boolean)
    
    /**
     * 批量删除记忆
     */
    @Query("DELETE FROM long_term_memories WHERE id IN (:ids)")
    suspend fun deleteMemoriesByIds(ids: List<Long>)
    
    /**
     * 获取最重要的激活记忆（用于AI对话）
     */
    @Query("""
        SELECT * FROM long_term_memories 
        WHERE isActive = 1 
        ORDER BY importance DESC, lastUsedAt DESC, createdAt DESC
        LIMIT :limit
    """)
    suspend fun getTopActiveMemories(limit: Int = 10): List<LongTermMemory>
}
