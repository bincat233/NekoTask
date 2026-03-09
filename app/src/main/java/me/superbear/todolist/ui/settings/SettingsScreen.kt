package me.superbear.todolist.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.superbear.todolist.assistant.subtask.DivisionStrategy
import me.superbear.todolist.domain.entities.LongTermMemory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    settingsState: SettingsState = SettingsState(),
    onSettingsChange: (SettingsState) -> Unit = {},
    onAddMemory: (String, String, Int, Boolean) -> Unit = { _, _, _, _ -> },
    onEditMemory: (LongTermMemory, String, String, Int, Boolean) -> Unit = { _, _, _, _, _ -> },
    onDeleteMemory: (LongTermMemory) -> Unit = {},
    onToggleMemoryActive: (LongTermMemory, Boolean) -> Unit = { _, _ -> }
) {
    var currentSettings by remember { mutableStateOf(settingsState) }
    
    // 当外部settingsState改变时，更新本地状态
    LaunchedEffect(settingsState) {
        currentSettings = settingsState
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("设置") 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // AI子任务划分设置
            AISubtaskDivisionSettings(
                settings = currentSettings,
                onSettingsChange = { newSettings ->
                    currentSettings = newSettings
                    onSettingsChange(newSettings)
                }
            )
            
            // 长期记忆管理
            LongTermMemorySettings(
                settings = currentSettings,
                onSettingsChange = { newSettings ->
                    currentSettings = newSettings
                    onSettingsChange(newSettings)
                },
                onAddMemory = onAddMemory,
                onEditMemory = onEditMemory,
                onDeleteMemory = onDeleteMemory,
                onToggleMemoryActive = onToggleMemoryActive
            )
        }
        
        // 长期记忆编辑对话框
        if (currentSettings.isMemoryDialogVisible) {
            LongTermMemoryDialog(
                memory = currentSettings.editingMemory,
                onDismiss = {
                    onSettingsChange(currentSettings.copy(
                        isMemoryDialogVisible = false,
                        editingMemory = null
                    ))
                },
                onSave = { content, category, importance, isActive ->
                    if (currentSettings.editingMemory != null) {
                        onEditMemory(currentSettings.editingMemory!!, content, category, importance, isActive)
                    } else {
                        onAddMemory(content, category, importance, isActive)
                    }
                    onSettingsChange(currentSettings.copy(
                        isMemoryDialogVisible = false,
                        editingMemory = null
                    ))
                }
            )
        }
    }
}

@Composable
private fun AISubtaskDivisionSettings(
    settings: SettingsState,
    onSettingsChange: (SettingsState) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AI子任务划分",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            HorizontalDivider()
            
            // 启用AI选项
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = settings.useAI,
                    onCheckedChange = { useAI ->
                        onSettingsChange(settings.copy(useAI = useAI))
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "启用AI智能划分",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (settings.useAI) "基于任务内容智能生成子任务" else "使用预设模板生成子任务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 划分策略选择
            Text(
                text = "划分策略",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DivisionStrategy.values().forEach { strategy ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = settings.aiDivisionStrategy == strategy,
                                onClick = {
                                    onSettingsChange(settings.copy(aiDivisionStrategy = strategy))
                                }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.aiDivisionStrategy == strategy,
                            onClick = {
                                onSettingsChange(settings.copy(aiDivisionStrategy = strategy))
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = getStrategyDisplayName(strategy),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = getStrategyDescription(strategy),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // 最大子任务数量
            Text(
                text = "最大子任务数量: ${settings.maxSubtasks}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Slider(
                value = settings.maxSubtasks.toFloat(),
                onValueChange = { value ->
                    onSettingsChange(settings.copy(maxSubtasks = value.toInt()))
                },
                valueRange = 2f..10f,
                steps = 7,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = "控制每次划分生成的子任务数量上限",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
        }
    }
}

/**
 * 获取策略显示名称
 */
private fun getStrategyDisplayName(strategy: DivisionStrategy): String {
    return when (strategy) {
        DivisionStrategy.DETAILED -> "详细划分"
        DivisionStrategy.BALANCED -> "平衡划分"
        DivisionStrategy.SIMPLIFIED -> "简化划分"
    }
}

/**
 * 获取策略描述
 */
private fun getStrategyDescription(strategy: DivisionStrategy): String {
    return when (strategy) {
        DivisionStrategy.DETAILED -> "生成更多细粒度的子任务，适合复杂项目"
        DivisionStrategy.BALANCED -> "适中的子任务数量和粒度，推荐选择"
        DivisionStrategy.SIMPLIFIED -> "生成较少的高层次子任务，适合简单任务"
    }
}

/**
 * 长期记忆设置组件
 */
@Composable
private fun LongTermMemorySettings(
    settings: SettingsState,
    onSettingsChange: (SettingsState) -> Unit,
    onAddMemory: (String, String, Int, Boolean) -> Unit,
    onEditMemory: (LongTermMemory, String, String, Int, Boolean) -> Unit,
    onDeleteMemory: (LongTermMemory) -> Unit,
    onToggleMemoryActive: (LongTermMemory, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI助手长期记忆",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = {
                        onSettingsChange(settings.copy(
                            isMemoryDialogVisible = true,
                            editingMemory = null
                        ))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加记忆"
                    )
                }
            }
            
            Text(
                text = "管理AI助手的长期记忆，这些信息将在每次对话中提供给AI",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (settings.longTermMemories.isEmpty()) {
                Text(
                    text = "暂无记忆，点击右上角 + 按钮添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(settings.longTermMemories) { memory ->
                        MemoryItem(
                            memory = memory,
                            onEdit = {
                                onSettingsChange(settings.copy(
                                    isMemoryDialogVisible = true,
                                    editingMemory = memory
                                ))
                            },
                            onDelete = { onDeleteMemory(memory) },
                            onToggleActive = { isActive ->
                                onToggleMemoryActive(memory, isActive)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 记忆项组件
 */
@Composable
private fun MemoryItem(
    memory: LongTermMemory,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (memory.isActive) 
                MaterialTheme.colorScheme.surfaceVariant 
            else 
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = memory.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (memory.isActive) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = memory.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        repeat(memory.importance) {
                            Text(
                                text = "★",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = memory.isActive,
                        onCheckedChange = onToggleActive,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑记忆",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除记忆",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
