package me.superbear.todolist.ui.main.sections.longTermMemory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.superbear.todolist.data.repository.LongTermMemoryRepository
import me.superbear.todolist.domain.entities.LongTermMemory

/**
 * Owns long-term memory CRUD orchestration and the observed memory list.
 */
class LongTermMemoryCoordinator(
    private val longTermMemoryRepository: LongTermMemoryRepository,
    private val viewModelScope: CoroutineScope
) {
    private val _memories = MutableStateFlow<List<LongTermMemory>>(emptyList())
    val memories: StateFlow<List<LongTermMemory>> = _memories.asStateFlow()

    init {
        viewModelScope.launch {
            longTermMemoryRepository.getAllMemories().collect { memories ->
                _memories.value = memories
            }
        }
    }

    fun handleEvent(event: LongTermMemoryEvent) {
        when (event) {
            is LongTermMemoryEvent.AddMemory -> {
                viewModelScope.launch {
                    longTermMemoryRepository.createMemory(
                        content = event.content,
                        category = event.category,
                        importance = event.importance,
                        isActive = event.isActive
                    )
                }
            }
            is LongTermMemoryEvent.EditMemory -> {
                viewModelScope.launch {
                    longTermMemoryRepository.updateMemory(
                        id = event.memory.id,
                        content = event.content,
                        category = event.category,
                        importance = event.importance,
                        isActive = event.isActive
                    )
                }
            }
            is LongTermMemoryEvent.DeleteMemory -> {
                viewModelScope.launch {
                    longTermMemoryRepository.deleteMemory(event.memory.id)
                }
            }
            is LongTermMemoryEvent.ToggleMemoryActive -> {
                viewModelScope.launch {
                    longTermMemoryRepository.toggleMemoryActive(event.memory.id, event.isActive)
                }
            }
            is LongTermMemoryEvent.LoadMemories -> {
                // Flow based, no explicit load needed if UI collects
            }
        }
    }
}
