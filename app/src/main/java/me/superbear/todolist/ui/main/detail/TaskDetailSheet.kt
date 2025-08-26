package me.superbear.todolist.ui.main.detail

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.setValue
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import android.view.Gravity
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animateDpAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.max
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
 * Sheet state for TaskDetailSheet
 */
enum class SheetState {
    HIDDEN,
    HALF_SCREEN,
    FULL_SCREEN
}

/**
 * TaskDetailSheet - A dialog-based sheet for displaying and editing task details
 * 
 * Features:
 * - Three-state BottomSheet behavior (Hidden, Half-screen, Full-screen)
 * - Drag gestures for state transitions
 * - IME-aware expansion with 300ms delay
 * - Outside click dismiss support
 * - No dim background for clean overlay
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    visible: Boolean,
    task: Task?,
    title: String,
    onTitleChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onToggleDone: (Task, Boolean) -> Unit,
    onChangeDue: (Task) -> Unit,
    onChangePriority: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val screenHeight = configuration.screenHeightDp.dp
    
    // Sheet state management
    var sheetState by remember { mutableStateOf(SheetState.HIDDEN) }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    
    // Initialize sheet state when visible changes
    LaunchedEffect(visible) {
        sheetState = if (visible) SheetState.HALF_SCREEN else SheetState.HIDDEN
    }
    
    // Auto-expand to fullscreen when IME appears (with 300ms delay)
    LaunchedEffect(imeVisible) {
        if (visible && imeVisible && sheetState != SheetState.FULL_SCREEN) {
            delay(300) // 300ms delay as requested
            if (imeVisible) { // Check again in case IME was dismissed during delay
                Log.d("TaskDetailSheet", "IME visible, expanding to fullscreen after 300ms")
                sheetState = SheetState.FULL_SCREEN
            }
        }
    }
    
    // Handle state changes
    LaunchedEffect(sheetState) {
        if (sheetState == SheetState.HIDDEN) {
            onDismiss()
        }
    }
    
    // Handle back button when sheet is visible
    BackHandler(enabled = sheetState != SheetState.HIDDEN) {
        when (sheetState) {
            SheetState.FULL_SCREEN -> sheetState = SheetState.HALF_SCREEN
            SheetState.HALF_SCREEN -> sheetState = SheetState.HIDDEN
            SheetState.HIDDEN -> { /* No action */ }
        }
    }


    if (sheetState != SheetState.HIDDEN && task != null) {
        Dialog(
            onDismissRequest = { sheetState = SheetState.HIDDEN },
            properties = DialogProperties(
                dismissOnClickOutside = true,
                dismissOnBackPress = true,
                usePlatformDefaultWidth = false
            )
        ) {
            // Configure dialog window properties
            (LocalView.current.parent as DialogWindowProvider).window.apply {
                setGravity(Gravity.BOTTOM)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) // Remove dim background
                
                // Set layout parameters for proper positioning
                attributes = attributes.apply {
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = when (sheetState) {
                        SheetState.FULL_SCREEN -> WindowManager.LayoutParams.MATCH_PARENT
                        SheetState.HALF_SCREEN -> WindowManager.LayoutParams.WRAP_CONTENT
                        SheetState.HIDDEN -> WindowManager.LayoutParams.WRAP_CONTENT
                    }
                }
            }
            
            // Main content container with layered toolbar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        when (sheetState) {
                            SheetState.FULL_SCREEN -> Modifier.fillMaxSize()
                            SheetState.HALF_SCREEN -> Modifier.fillMaxHeight(0.6f)
                            SheetState.HIDDEN -> Modifier.fillMaxHeight(0f)
                        }
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = if (sheetState == SheetState.FULL_SCREEN) {
                            RoundedCornerShape(0.dp)
                        } else {
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        }
                    )
            ) {
                // Child 1: Main sheet content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 80.dp) // Bottom padding for toolbar height + 16dp
                ) {
                    // Drag handle (always show, but different behavior based on state)
                    var totalDragAmount by remember { mutableStateOf(0f) }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .height(32.dp)
                            .pointerInput(sheetState) {
                                detectDragGestures(
                                    onDragStart = {
                                        totalDragAmount = 0f
                                    },
                                    onDragEnd = {
                                        val dragThreshold = 150f
                                        
                                        when (sheetState) {
                                            SheetState.HALF_SCREEN -> {
                                                if (totalDragAmount > dragThreshold) {
                                                    // Drag down to dismiss
                                                    sheetState = SheetState.HIDDEN
                                                } else if (totalDragAmount < -dragThreshold) {
                                                    // Drag up to fullscreen
                                                    sheetState = SheetState.FULL_SCREEN
                                                }
                                            }
                                            SheetState.FULL_SCREEN -> {
                                                if (totalDragAmount > dragThreshold) {
                                                    // Drag down to half screen
                                                    sheetState = SheetState.HALF_SCREEN
                                                }
                                            }
                                            SheetState.HIDDEN -> { /* No action */ }
                                        }
                                        totalDragAmount = 0f
                                    }
                                ) { change, dragAmount ->
                                    // Accumulate drag amount
                                    totalDragAmount += dragAmount.y
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .clickable {
                                    // Tap to toggle between half and full screen
                                    sheetState = when (sheetState) {
                                        SheetState.HALF_SCREEN -> SheetState.FULL_SCREEN
                                        SheetState.FULL_SCREEN -> SheetState.HALF_SCREEN
                                        SheetState.HIDDEN -> SheetState.HALF_SCREEN
                                    }
                                }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(if (sheetState == SheetState.FULL_SCREEN) 16.dp else 8.dp))
                    
                    // Header section with checkbox, due date, and priority
                    HeaderSection(
                        task = task,
                        onToggleDone = onToggleDone,
                        onChangeDue = onChangeDue,
                        onChangePriority = onChangePriority
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Title section
                    TitleSection(
                        title = title,
                        onTitleChange = onTitleChange
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Content section (scrollable)
                    ContentSection(
                        content = content,
                        onContentChange = onContentChange,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Child 2: Toolbar overlaid at bottom
                TaskDetailToolbar(
                    task = task,
                    onToggleDone = onToggleDone,
                    onChangeDue = onChangeDue,
                    onChangePriority = onChangePriority,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(bottom = 50.dp)
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
    title: String,
    onTitleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    
    TextField(
        value = title,
        onValueChange = onTitleChange,
        placeholder = { Text("Title") },
        textStyle = MaterialTheme.typography.titleLarge,
        singleLine = true,
        maxLines = 1,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

@Composable
private fun ContentSection(
    content: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TextField(
            value = content,
            onValueChange = onContentChange,
            placeholder = { Text("Add details…") },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 3,
            maxLines = 12,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Default
            ),
            modifier = Modifier.fillMaxWidth()
        )
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
