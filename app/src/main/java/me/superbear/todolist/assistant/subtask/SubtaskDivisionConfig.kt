package me.superbear.todolist.assistant.subtask

/**
 * Subtask Division Configuration
 */
data class SubtaskDivisionConfig(
    val defaultStrategy: DivisionStrategy = DivisionStrategy.BALANCED,
    val defaultMaxSubtasks: Int = 5,
    val enableDependencyAnalysis: Boolean = true,
    val enablePriorityInheritance: Boolean = true, // Whether subtasks inherit parent task priority
    val autoCreateSubtasks: Boolean = false, // Whether to automatically create subtasks in database
    val requireUserConfirmation: Boolean = true // Whether user confirmation is required before creating subtasks
)

/**
 * Subtask Division Configuration Manager
 */
object SubtaskDivisionConfigManager {
    private var config = SubtaskDivisionConfig()
    
    fun getConfig(): SubtaskDivisionConfig = config
    
    fun updateConfig(newConfig: SubtaskDivisionConfig) {
        config = newConfig
    }
    
    fun updateStrategy(strategy: DivisionStrategy) {
        config = config.copy(defaultStrategy = strategy)
    }
    
    fun updateMaxSubtasks(maxSubtasks: Int) {
        config = config.copy(defaultMaxSubtasks = maxSubtasks.coerceIn(1, 10))
    }
    
    fun toggleAutoCreate(enabled: Boolean) {
        config = config.copy(autoCreateSubtasks = enabled)
    }
    
    fun toggleUserConfirmation(enabled: Boolean) {
        config = config.copy(requireUserConfirmation = enabled)
    }
}
