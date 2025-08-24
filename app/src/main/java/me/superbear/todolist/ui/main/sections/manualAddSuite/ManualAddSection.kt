package me.superbear.todolist.ui.main.sections.manualAddSuite

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.superbear.todolist.domain.entities.Priority

/**
 * Manual add section - title/desc fields + open/close
 */
@Composable
fun ManualAddSection(
    state: ManualAddState,
    dateTimePickerState: DateTimePickerState,
    priorityState: PriorityState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onDueDateClick: () -> Unit,
    onPriorityClick: () -> Unit,
    onPrioritySelected: (Priority) -> Unit,
    onPriorityDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isOpen,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Manual Add Task")
                TextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    placeholder = { Text("What would you like to do?") },
                    textStyle = MaterialTheme.typography.titleMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.padding(8.dp))
                TextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    placeholder = { Text("Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDueDateClick) {
                        Icon(
                            Icons.Default.Event, 
                            contentDescription = "Due date",
                            tint = if (dateTimePickerState.selectedDueDateMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Priority selector
                    Box {
                        val priorityTint = when (priorityState.selectedPriority) {
                            Priority.HIGH -> Color(0xFFE53935)  // red
                            Priority.MEDIUM -> Color(0xFFFFA000) // amber
                            Priority.LOW -> Color(0xFF43A047)    // green
                            Priority.DEFAULT -> MaterialTheme.colorScheme.onSurface
                        }
                        IconButton(onClick = onPriorityClick) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "Priority",
                                tint = priorityTint
                            )
                        }
                        DropdownMenu(
                            expanded = priorityState.isMenuVisible,
                            onDismissRequest = onPriorityDismiss
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) { 
                                        Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFE53935))
                                        Spacer(Modifier.width(8.dp))
                                        Text("High") 
                                    } 
                                },
                                onClick = { onPrioritySelected(Priority.HIGH) }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) { 
                                        Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFFFA000))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Medium") 
                                    } 
                                },
                                onClick = { onPrioritySelected(Priority.MEDIUM) }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) { 
                                        Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFF43A047))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Low") 
                                    } 
                                },
                                onClick = { onPrioritySelected(Priority.LOW) }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) { 
                                        Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                        Spacer(Modifier.width(8.dp))
                                        Text("None") 
                                    } 
                                },
                                onClick = { onPrioritySelected(Priority.DEFAULT) }
                            )
                        }
                    }
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Filled.FormatListBulleted, contentDescription = "Subtasks")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Button(onClick = onSubmit, enabled = state.title.isNotBlank()) {
                        Text("Send")
                    }
                }
            }
        }
    }
}
