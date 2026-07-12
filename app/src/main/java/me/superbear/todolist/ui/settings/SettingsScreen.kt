package me.superbear.todolist.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.R
import ai.koog.prompt.llm.LLMProvider
import me.superbear.todolist.assistant.subtask.DivisionStrategy
import me.superbear.todolist.domain.entities.LongTermMemory

data class ProviderDisplayInfo(
    val displayName: String,
    val fallbackModels: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    settingsState: SettingsState = SettingsState(),
    onSettingsChange: (SettingsState) -> Unit = {},
    selectedProvider: LLMProvider = LLMProvider.OpenAI,
    onProviderSelect: (LLMProvider) -> Unit = {},
    apiKey: String = "",
    onApiKeySave: (LLMProvider, String) -> Unit = { _, _ -> },
    selectedModel: String = "",
    availableModels: List<String> = emptyList(),
    isLoadingModels: Boolean = false,
    onModelSelect: (String) -> Unit = {},
    onRefreshModels: () -> Unit = {},
    onAddMemory: (String, String, Int, Boolean) -> Unit = { _, _, _, _ -> },
    onEditMemory: (LongTermMemory, String, String, Int, Boolean) -> Unit = { _, _, _, _, _ -> },
    onDeleteMemory: (LongTermMemory) -> Unit = {},
    onToggleMemoryActive: (LongTermMemory, Boolean) -> Unit = { _, _ -> },
    onLanguageChange: (String) -> Unit = {},
    providerInfo: Map<LLMProvider, ProviderDisplayInfo> = emptyMap(),
    onResetSampleData: () -> Unit = {}
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
                    Text(stringResource(R.string.settings_title)) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // AI 供应商选择
            ProviderSection(
                selectedProvider = selectedProvider,
                onProviderSelect = onProviderSelect,
                providerInfo = providerInfo
            )

            // 语言设置
            LanguageSection(
                currentLanguage = currentSettings.currentLanguage,
                onLanguageChange = onLanguageChange
            )

            // API 密钥设置
            ApiKeySection(
                provider = selectedProvider,
                apiKey = apiKey,
                onApiKeySave = onApiKeySave,
                providerInfo = providerInfo
            )

            // 模型选择
            val fallback = providerInfo[selectedProvider]?.fallbackModels ?: emptyList()
            ModelSection(
                selectedModel = selectedModel,
                availableModels = availableModels,
                isLoading = isLoadingModels,
                onModelSelect = onModelSelect,
                onRefresh = onRefreshModels,
                fallbackModels = fallback
            )

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

            // 开发者选项（仅 debug 包）
            if (BuildConfig.DEBUG) {
                DeveloperSection(onResetSampleData = onResetSampleData)
            }
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
private fun LanguageSection(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            val languages = listOf(
                "auto" to stringResource(R.string.language_auto),
                "en" to stringResource(R.string.language_en),
                "zh" to stringResource(R.string.language_zh)
            )

            // 提取基础标签，例如将 "zh-CN" 简化为 "zh" 以便匹配
            val baseLanguage = currentLanguage.split("-").first().lowercase()

            languages.forEach { (tag, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (tag == "auto" && currentLanguage == "auto") || (tag != "auto" && baseLanguage == tag),
                            onClick = { onLanguageChange(tag) }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (tag == "auto" && currentLanguage == "auto") || (tag != "auto" && baseLanguage == tag),
                        onClick = { onLanguageChange(tag) }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSection(
    selectedProvider: LLMProvider,
    onProviderSelect: (LLMProvider) -> Unit,
    providerInfo: Map<LLMProvider, ProviderDisplayInfo>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_ai_provider),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            providerInfo.forEach { (provider, info) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedProvider == provider,
                            onClick = { onProviderSelect(provider) }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedProvider == provider,
                        onClick = { onProviderSelect(provider) }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(info.displayName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSection(
    selectedModel: String,
    availableModels: List<String>,
    isLoading: Boolean,
    onModelSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    fallbackModels: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    val displayModels = availableModels.ifEmpty { fallbackModels }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_model_title), style = MaterialTheme.typography.titleMedium)
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.settings_model_refresh),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    label = { Text(stringResource(R.string.settings_model_select)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    displayModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                onModelSelect(model)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            Text(
                text = if (availableModels.isNotEmpty())
                    stringResource(R.string.settings_model_count_msg, availableModels.size, "OpenAI")
                else
                    stringResource(R.string.settings_model_fallback_msg, "OpenAI"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApiKeySection(
    provider: LLMProvider,
    apiKey: String,
    onApiKeySave: (LLMProvider, String) -> Unit,
    providerInfo: Map<LLMProvider, ProviderDisplayInfo>
) {
    val displayName = providerInfo[provider]?.displayName ?: provider.display
    var draft by remember(apiKey) { mutableStateOf(apiKey) }
    var visible by remember { mutableStateOf(false) }
    val changed = draft.trim() != apiKey.trim() && draft.isNotBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_api_key_title, displayName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_api_key_label, displayName)) },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (visible) stringResource(R.string.hide) else stringResource(R.string.show)
                        )
                    }
                }
            )

            Text(
                text = stringResource(R.string.settings_api_key_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { onApiKeySave(provider, draft) },
                enabled = changed,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.save))
            }
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
                text = stringResource(R.string.settings_ai_division_title),
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
                        text = stringResource(R.string.settings_ai_division_enable),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (settings.useAI) stringResource(R.string.settings_ai_division_enable_desc) else stringResource(R.string.settings_ai_division_template_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 划分策略选择
            Text(
                text = stringResource(R.string.settings_ai_strategy_title),
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
                text = stringResource(R.string.settings_max_subtasks_title, settings.maxSubtasks),
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
                text = stringResource(R.string.settings_max_subtasks_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
        }
    }
}

/**
 * 获取策略显示名称
 */
@Composable
private fun getStrategyDisplayName(strategy: DivisionStrategy): String {
    return when (strategy) {
        DivisionStrategy.DETAILED -> stringResource(R.string.strategy_detailed)
        DivisionStrategy.BALANCED -> stringResource(R.string.strategy_balanced)
        DivisionStrategy.SIMPLIFIED -> stringResource(R.string.strategy_simplified)
    }
}

/**
 * 获取策略描述
 */
@Composable
private fun getStrategyDescription(strategy: DivisionStrategy): String {
    return when (strategy) {
        DivisionStrategy.DETAILED -> stringResource(R.string.strategy_detailed_desc)
        DivisionStrategy.BALANCED -> stringResource(R.string.strategy_balanced_desc)
        DivisionStrategy.SIMPLIFIED -> stringResource(R.string.strategy_simplified_desc)
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
                    text = stringResource(R.string.memory_title),
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
                        contentDescription = stringResource(R.string.memory_add_content_desc)
                    )
                }
            }
            
            Text(
                text = stringResource(R.string.memory_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (settings.longTermMemories.isEmpty()) {
                Text(
                    text = stringResource(R.string.memory_empty),
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
                            contentDescription = stringResource(R.string.memory_edit_content_desc),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.memory_delete_content_desc),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Debug-only developer settings. Never shown in release builds (gated by BuildConfig.DEBUG
 * at the call site) - lets a developer wipe all tasks and re-seed the built-in sample data
 * without reinstalling the app.
 */
@Composable
private fun DeveloperSection(
    onResetSampleData: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.dev_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.dev_reset_sample_data_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { showConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.dev_reset_sample_data))
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.dev_reset_sample_data_confirm_title)) },
            text = { Text(stringResource(R.string.dev_reset_sample_data_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onResetSampleData()
                }) {
                    Text(stringResource(R.string.dev_reset_sample_data))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
