package me.superbear.todolist.assistant.subtask

/**
 * Subtask Divider Interface
 */
interface SubtaskDivider {
    /**
     * Divide task into subtasks
     * @param request Division request
     * @return Division result, returns error information on failure
     */
    suspend fun divideTask(request: SubtaskDivisionRequest): Result<SubtaskDivisionResponse>
}

/**
 * Mock Subtask Divider (for testing)
 */
class MockSubtaskDivider : SubtaskDivider {
    override suspend fun divideTask(request: SubtaskDivisionRequest): Result<SubtaskDivisionResponse> {
        val subtasks = when (request.strategy) {
            DivisionStrategy.DETAILED -> listOf(
                SubtaskSuggestion("Analyze requirements for ${request.taskTitle}", "Detailed analysis of task requirements and goals", request.taskPriority, 1),
                SubtaskSuggestion("Create execution plan", "Develop specific implementation steps", request.taskPriority, 2, listOf(1)),
                SubtaskSuggestion("Prepare necessary resources", "Collect and prepare required tools and materials", request.taskPriority, 3, listOf(2)),
                SubtaskSuggestion("Begin execution", "Start implementation according to plan", request.taskPriority, 4, listOf(3)),
                SubtaskSuggestion("Review and refine", "Check results and make necessary adjustments", request.taskPriority, 5, listOf(4))
            )
            DivisionStrategy.BALANCED -> listOf(
                SubtaskSuggestion("Preparation phase", "Analyze requirements and create plan", request.taskPriority, 1),
                SubtaskSuggestion("Execution phase", "Implement task according to plan", request.taskPriority, 2, listOf(1)),
                SubtaskSuggestion("Acceptance phase", "Check results and refine", request.taskPriority, 3, listOf(2))
            )
            DivisionStrategy.SIMPLIFIED -> listOf(
                SubtaskSuggestion("Start ${request.taskTitle}", "Initiate and execute main work", request.taskPriority, 1),
                SubtaskSuggestion("Complete ${request.taskTitle}", "Finish and check work results", request.taskPriority, 2, listOf(1))
            )
        }
        
        return Result.success(
            SubtaskDivisionResponse(
                originalTask = request.taskTitle,
                subtasks = subtasks.take(request.maxSubtasks),
                reasoning = "Mock subtasks generated based on ${request.strategy.name} strategy"
            )
        )
    }
}
