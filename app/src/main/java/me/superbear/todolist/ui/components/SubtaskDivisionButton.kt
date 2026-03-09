package me.superbear.todolist.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.superbear.todolist.assistant.subtask.DivisionStrategy

/**
 * Subtask Division Button Component
 */
@Composable
fun SubtaskDivisionButton(
    onDivideTask: (strategy: DivisionStrategy, useAI: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showStrategyDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showStrategyDialog = true },
        enabled = enabled,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "AI Subtask Division",
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("AI Divide Task")
    }

    if (showStrategyDialog) {
        SubtaskDivisionStrategyDialog(
            onDismiss = { showStrategyDialog = false },
            onConfirm = { strategy, useAI ->
                showStrategyDialog = false
                onDivideTask(strategy, useAI)
            }
        )
    }
}

/**
 * Subtask Division Strategy Selection Dialog
 */
@Composable
private fun SubtaskDivisionStrategyDialog(
    onDismiss: () -> Unit,
    onConfirm: (strategy: DivisionStrategy, useAI: Boolean) -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(DivisionStrategy.BALANCED) }
    var useAI by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Division Strategy") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose the level of detail for subtask division:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // 策略选择
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DivisionStrategy.values().forEach { strategy ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedStrategy == strategy,
                                onClick = { selectedStrategy = strategy }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = getStrategyDisplayName(strategy),
                                    style = MaterialTheme.typography.bodyMedium
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

                Divider()

                // AI选项
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = useAI,
                        onCheckedChange = { useAI = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Use AI Smart Division",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (useAI) "Generate subtasks intelligently based on task content" else "Generate subtasks using preset templates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedStrategy, useAI) }
            ) {
                Text("Start Division")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Get strategy display name
 */
private fun getStrategyDisplayName(strategy: DivisionStrategy): String {
    return when (strategy) {
        DivisionStrategy.DETAILED -> "Detailed Division"
        DivisionStrategy.BALANCED -> "Balanced Division"
        DivisionStrategy.SIMPLIFIED -> "Simplified Division"
    }
}

/**
 * Get strategy description
 */
private fun getStrategyDescription(strategy: DivisionStrategy): String {
    return when (strategy) {
        DivisionStrategy.DETAILED -> "Generate more fine-grained subtasks, suitable for complex projects"
        DivisionStrategy.BALANCED -> "Moderate number and granularity of subtasks, recommended choice"
        DivisionStrategy.SIMPLIFIED -> "Generate fewer high-level subtasks, suitable for simple tasks"
    }
}
