package me.superbear.todolist.ui.main.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import me.superbear.todolist.domain.entities.Task

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
                
                // Header section placeholder
                HeaderSection(task = task)
                
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Header Section - Task ID: ${task.id}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
