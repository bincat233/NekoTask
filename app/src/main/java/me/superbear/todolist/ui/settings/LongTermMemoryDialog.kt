package me.superbear.todolist.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import me.superbear.todolist.R
import me.superbear.todolist.domain.entities.LongTermMemory
import me.superbear.todolist.domain.entities.MemoryCategory

/**
 * 长期记忆编辑对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongTermMemoryDialog(
    memory: LongTermMemory?,
    onDismiss: () -> Unit,
    onSave: (content: String, category: String, importance: Int, isActive: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var content by remember { mutableStateOf(memory?.content ?: "") }
    var selectedCategory by remember { mutableStateOf(memory?.category ?: "general") }
    var importance by remember { mutableIntStateOf(memory?.importance ?: 3) }
    var isActive by remember { mutableStateOf(memory?.isActive ?: true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 标题
                Text(
                    text = if (memory == null) stringResource(R.string.memory_add_title) else stringResource(R.string.memory_edit_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // 记忆内容输入
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.memory_content_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text(stringResource(R.string.memory_content_placeholder)) }
                )

                // 类别选择
                Column {
                    Text(
                        text = stringResource(R.string.memory_category_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    MemoryCategory.values().forEach { category ->
                        val categoryValue = when (category) {
                            MemoryCategory.GENERAL -> "general"
                            MemoryCategory.PREFERENCES -> "preferences"
                            MemoryCategory.WORK_HABITS -> "work_habits"
                            MemoryCategory.PROJECT_INFO -> "project_info"
                            MemoryCategory.PERSONAL -> "personal"
                            MemoryCategory.CONTEXT -> "context"
                        }
                        val categoryDisplayName = when (category) {
                            MemoryCategory.GENERAL -> stringResource(R.string.cat_general)
                            MemoryCategory.PREFERENCES -> stringResource(R.string.cat_preferences)
                            MemoryCategory.WORK_HABITS -> stringResource(R.string.cat_work_habits)
                            MemoryCategory.PROJECT_INFO -> stringResource(R.string.cat_project_info)
                            MemoryCategory.PERSONAL -> stringResource(R.string.cat_personal)
                            MemoryCategory.CONTEXT -> stringResource(R.string.cat_context)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedCategory == categoryValue,
                                    onClick = { selectedCategory = categoryValue }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategory == categoryValue,
                                onClick = { selectedCategory = categoryValue }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = categoryDisplayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // 重要性滑块
                Column {
                    Text(
                        text = stringResource(R.string.memory_importance_label, importance),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = importance.toFloat(),
                        onValueChange = { importance = it.toInt() },
                        valueRange = 1f..5f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.memory_importance_low),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.memory_importance_high),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 激活状态开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.memory_active_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                }

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (content.isNotBlank()) {
                                onSave(content, selectedCategory, importance, isActive)
                            }
                        },
                        enabled = content.isNotBlank()
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}
