package me.superbear.todolist.ui.main.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerSection
import me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerState
import kotlinx.datetime.Instant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TaskDetailSheet - A bottom sheet for displaying and editing task details
 * 
 * Features:
 * - Material3 ModalBottomSheet with drag handle
 * - Default state: PartiallyExpanded (half-height)
 * - Supports drag to Expanded (full-height) 
 * - Swipe down to Hidden triggers onDismiss
 * - BackHandler support for closing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    visible: Boolean,
    task: Task?,
    onDismiss: () -> Unit,
    onToggleDone: (Task, Boolean) -> Unit,
    onChangeDue: (Task) -> Unit,
    onChangePriority: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    // Bottom sheet state - allows PartiallyExpanded (half-height) by default
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    // Handle back button when sheet is visible
    BackHandler(enabled = visible) {
        onDismiss()
    }

    // Auto-dismiss when sheet is hidden via swipe
    LaunchedEffect(sheetState.isVisible) {
        if (!sheetState.isVisible && visible) {
            onDismiss()
        }
    }

    if (visible && task != null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Header section with checkbox, due date, and priority
                HeaderSection(
                    task = task,
                    onToggleDone = onToggleDone,
                    onChangeDue = onChangeDue,
                    onChangePriority = onChangePriority
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Title section placeholder
                TitleSection(task = task)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Content section placeholder
                ContentSection(
                    task = task,
                    modifier = Modifier.weight(1f) // Make content section expand
                )
                
                // 底部空白区域，为工具栏预留空间
                Spacer(modifier = Modifier.height(80.dp)) // 工具栏高度 + padding
            }
        }
        
        // 底部工具栏：使用 Dialog 覆盖在 BottomSheet 之上
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnClickOutside = false,
                dismissOnBackPress = false,
                usePlatformDefaultWidth = false
            )
        ) {
            // Configure dialog window to overlay above BottomSheet
            (LocalView.current.parent as DialogWindowProvider).window.apply {
                setDimAmount(0f) // No dimming
                addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }
            
            // Full-screen container with toolbar at bottom
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                TaskDetailToolbar(
                    task = task,
                    onToggleDone = onToggleDone,
                    onChangeDue = onChangeDue,
                    onChangePriority = onChangePriority,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 32.dp)
                )
            }
        }
    }
}


@Composable
private fun HeaderSection(
    task: Task,
    onToggleDone: (Task, Boolean) -> Unit,
    onChangeDue: (Task) -> Unit,
    onChangePriority: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showPriorityMenu by remember { mutableStateOf(false) }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Left: Checkbox to toggle task done
        Checkbox(
            checked = task.status == TaskStatus.DONE,
            onCheckedChange = { onToggleDone(task, it) }
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Middle: Due date chip (weighted to take remaining space)
        DueChip(
            dueAt = task.dueAt,
            onClick = { showDatePicker = true },
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Right: Priority flag
        PriorityFlag(
            priority = task.priority,
            onClick = { showPriorityMenu = true }
        )
    }
    
    // Due date picker
    if (showDatePicker) {
        DateTimePickerSection(
            state = DateTimePickerState(
                isVisible = true,
                selectedDueDateMs = task.dueAt?.toEpochMilliseconds()
            ),
            onDateTimeSelected = { timestamp ->
                onChangeDue(task)
                showDatePicker = false
            },
            onCancel = {
                showDatePicker = false
            }
        )
    }
    
    // Priority menu
    if (showPriorityMenu) {
        PriorityDropdown(
            currentPriority = task.priority,
            onPrioritySelected = { priority ->
                onChangePriority(task)
                showPriorityMenu = false
            },
            onDismiss = {
                showPriorityMenu = false
            }
        )
    }
}

@Composable
private fun DueChip(
    dueAt: Instant?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable { onClick() }
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = "Due date",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(6.dp))
        
        Text(
            text = if (dueAt != null) {
                formatDueDate(dueAt)
            } else {
                "No due date"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PriorityFlag(
    priority: Priority,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val priorityTint = when (priority) {
        Priority.HIGH -> Color(0xFFE53935)  // red
        Priority.MEDIUM -> Color(0xFFFFA000) // amber
        Priority.LOW -> Color(0xFF43A047)    // green
        Priority.DEFAULT -> MaterialTheme.colorScheme.onSurface
    }
    
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = "Priority",
            tint = priorityTint
        )
    }
}

@Composable
private fun PriorityDropdown(
    currentPriority: Priority,
    onPrioritySelected: (Priority) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = onDismiss
        ) {
            DropdownMenuItem(
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color(0xFFE53935)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("High") 
                    } 
                },
                onClick = { onPrioritySelected(Priority.HIGH) }
            )
            DropdownMenuItem(
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color(0xFFFFA000)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Medium") 
                    } 
                },
                onClick = { onPrioritySelected(Priority.MEDIUM) }
            )
            DropdownMenuItem(
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color(0xFF43A047)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Low") 
                    } 
                },
                onClick = { onPrioritySelected(Priority.LOW) }
            )
            DropdownMenuItem(
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) { 
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("None") 
                    } 
                },
                onClick = { onPrioritySelected(Priority.DEFAULT) }
            )
        }
    }
}

private fun formatDueDate(instant: Instant): String {
    val date = Date(instant.toEpochMilliseconds())
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(date)
}

@Composable
private fun TitleSection(
    task: Task,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Title",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun ContentSection(
    task: Task,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Content",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = task.content ?: "No content",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun TaskDetailToolbar(
    task: Task,
    onToggleDone: (Task, Boolean) -> Unit,
    onChangeDue: (Task) -> Unit,
    onChangePriority: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Placeholder buttons
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📅",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚡",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "Priority: ${task.priority}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
