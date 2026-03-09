package me.superbear.todolist.domain.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * AI助手的长期记忆实体
 */
@Entity(tableName = "long_term_memories")
data class LongTermMemory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 记忆内容
     */
    val content: String,
    
    /**
     * 记忆类型/标签
     */
    val category: String = "general",
    
    /**
     * 重要性等级 (1-5, 5最重要)
     */
    val importance: Int = 3,
    
    /**
     * 是否激活状态 (只有激活的记忆会被包含在AI对话中)
     */
    val isActive: Boolean = true,
    
    /**
     * 创建时间
     */
    val createdAt: Instant,
    
    /**
     * 最后更新时间
     */
    val updatedAt: Instant,
    
    /**
     * 最后使用时间 (用于记忆的优先级排序)
     */
    val lastUsedAt: Instant? = null
)

/**
 * 记忆类别枚举
 */
enum class MemoryCategory(val displayName: String) {
    GENERAL("通用"),
    PREFERENCES("偏好设置"),
    WORK_HABITS("工作习惯"),
    PROJECT_INFO("项目信息"),
    PERSONAL("个人信息"),
    CONTEXT("上下文信息")
}
