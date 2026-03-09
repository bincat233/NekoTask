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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import me.superbear.todolist.assistant.subtask.DivisionStrategy
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
    subtasks: List<Task>,
    onToggleSubtask: (String, Boolean) -> Unit,
    onEditSubtaskTitle: (String, String) -> Unit,
    onAddSubtask: (Long, String, Long?) -> Unit,
    onDeleteSubtask: (Long) -> Unit,
    onDismiss: () -> Unit,
    onToggleDone: (Task, Boolean) -> Unit,
    onChangeDue: (Task) -> Unit,
    onChangePriority: (Task, Priority) -> Unit,
    onDivideSubtasks: (Long) -> Unit,
    onDeleteTask: (Task) -> Unit,
    isSubtaskDivisionLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val screenHeight = configuration.screenHeightDp.dp
    
    // Sheet state management
    var sheetState by remember { mutableStateOf(SheetState.HIDDEN) }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    // Drag offset in pixels
    var dragOffsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Calculate animated height based on state
    val targetHeightFraction = when (sheetState) {
        SheetState.FULL_SCREEN -> 1f
        SheetState.HALF_SCREEN -> 0.6f
        SheetState.HIDDEN -> 0f
    }

    val animatedHeightFraction by animateFloatAsState(
        targetValue = targetHeightFraction,
        animationSpec = tween(durationMillis = 300),
        label = "sheetHeight"
    )

    // Initialize sheet state when visible changes
    LaunchedEffect(visible) {
        sheetState = if (visible) SheetState.HALF_SCREEN else SheetState.HIDDEN
        dragOffsetY = 0f
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
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) // Remove default dim background
                
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
            
            // Full screen container with custom background overlay
            Box(modifier = Modifier.fillMaxSize()) {
                // Background overlay with animated alpha
                val overlayAlpha by animateFloatAsState(
                    targetValue = if (sheetState == SheetState.HALF_SCREEN) 0.5f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "overlayAlpha"
                )

                if (overlayAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = overlayAlpha))
                            .clickable(enabled = sheetState == SheetState.HALF_SCREEN) {
                                sheetState = SheetState.HIDDEN
                            }
                    )
                }

                // Calculate final height: animated height + drag offset
                val screenHeightPx = with(density) { screenHeight.toPx() }
                val finalHeightFraction = if (isDragging) {
                    // When dragging, apply offset to current animated position
                    (animatedHeightFraction - dragOffsetY / screenHeightPx).coerceIn(0f, 1f)
                } else {
                    // When not dragging, just use animated height
                    animatedHeightFraction
                }

                // Main content container with layered toolbar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .fillMaxHeight(finalHeightFraction)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = if (finalHeightFraction >= 0.95f) {
                                RoundedCornerShape(0.dp)
                            } else {
                                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            shape = if (finalHeightFraction >= 0.95f) {
                                RoundedCornerShape(0.dp)
                            } else {
                                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            }
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            // Consume click events to prevent propagation to background overlay
                            // Do nothing - this prevents clicks inside the card from closing the sheet
                        }
            ) {
                // Child 1: Main sheet content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 80.dp) // Bottom padding for toolbar height + 16dp
                ) {
                    // Drag handle with smooth real-time dragging
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .height(32.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        isDragging = true
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        // Update drag offset in real-time
                                        dragOffsetY += dragAmount.y
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        val dragThreshold = 150f

                                        // Determine new state based on drag direction and amount
                                        when (sheetState) {
                                            SheetState.HALF_SCREEN -> {
                                                sheetState = when {
                                                    dragOffsetY > dragThreshold -> SheetState.HIDDEN
                                                    dragOffsetY < -dragThreshold -> SheetState.FULL_SCREEN
                                                    else -> SheetState.HALF_SCREEN
                                                }
                                            }
                                            SheetState.FULL_SCREEN -> {
                                                sheetState = if (dragOffsetY > dragThreshold) {
                                                    SheetState.HALF_SCREEN
                                                } else {
                                                    SheetState.FULL_SCREEN
                                                }
                                            }
                                            SheetState.HIDDEN -> { /* No action */ }
                                        }

                                        // Reset drag offset
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        dragOffsetY = 0f
                                    }
                                )
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
                    
                    // Subtasks section (between title and content)
                    if (subtasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SubtasksSection(
                            subtasks = subtasks,
                            onToggleSubtask = onToggleSubtask,
                            onEditSubtaskTitle = onEditSubtaskTitle,
                            onAddSubtask = onAddSubtask,
                            onDeleteSubtask = onDeleteSubtask
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Content section (no longer individually scrollable)
                    ContentSection(
                        content = content,
                        onContentChange = onContentChange
                    )
                }
                
                // Child 2: Toolbar overlaid at bottom
                TaskDetailToolbar(
                    task = task,
                    onToggleDone = onToggleDone,
                    onChangeDue = onChangeDue,
                    onChangePriority = onChangePriority,
                    onAddSubtask = onAddSubtask,
                    onDivideSubtasks = onDivideSubtasks,
                    onDeleteTask = onDeleteTask,
                    isSubtaskDivisionLoading = isSubtaskDivisionLoading,
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
}


@Composable
private fun HeaderSection(
    task: Task,
    onToggleDone: (Task, Boolean) -> Unit,
    onChangeDue: (Task) -> Unit,
    onChangePriority: (Task, Priority) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPriorityMenu by remember { mutableStateOf(false) }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Left: Checkbox to toggle task done
        val checkboxColors = CheckboxDefaults.colors(
            checkedColor = when (task.priority) {
                Priority.HIGH -> Color(0xFFE53935)     // red
                Priority.MEDIUM -> Color(0xFFFFA000)   // amber
                Priority.LOW -> Color(0xFF43A047)      // green
                Priority.DEFAULT -> MaterialTheme.colorScheme.primary
            },
            uncheckedColor = when (task.priority) {
                Priority.HIGH -> Color(0xFFE53935)     // red
                Priority.MEDIUM -> Color(0xFFFFA000)   // amber
                Priority.LOW -> Color(0xFF43A047)      // green
                Priority.DEFAULT -> MaterialTheme.colorScheme.onSurface
            },
            checkmarkColor = Color.White
        )
        
        Checkbox(
            checked = task.status == TaskStatus.DONE,
            onCheckedChange = { onToggleDone(task, it) },
            colors = checkboxColors
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Middle: Due date chip (weighted to take remaining space)
        DueChip(
            dueAt = task.dueAt,
            onClick = { onChangeDue(task) },
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Right: Priority flag with dropdown menu
        Box {
            PriorityFlag(
                priority = task.priority,
                onClick = { showPriorityMenu = true }
            )
            
            // Priority menu positioned relative to the flag
            if (showPriorityMenu) {
                PriorityDropdown(
                    currentPriority = task.priority,
                    onPrioritySelected = { priority ->
                        onChangePriority(task, priority)
                        showPriorityMenu = false
                    },
                    onDismiss = {
                        showPriorityMenu = false
                    }
                )
            }
        }
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
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
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
    val focusManager = LocalFocusManager.current
    
    TextField(
            value = content,
            onValueChange = onContentChange,
            placeholder = { Text("Add details…") },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 3,
            maxLines = 12,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Default
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                }
            ),
            modifier = modifier.fillMaxWidth()
        )
}


@Composable
fun TaskDetailToolbar(
    task: Task,
    onToggleDone: (Task, Boolean) -> Unit,
    onChangeDue: (Task) -> Unit,
    onChangePriority: (Task, Priority) -> Unit,
    onAddSubtask: (Long, String, Long?) -> Unit,
    onDivideSubtasks: (Long) -> Unit,
    onDeleteTask: (Task) -> Unit,
    isSubtaskDivisionLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // AI Subtask Division Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (isSubtaskDivisionLoading) 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else 
                        MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(enabled = !isSubtaskDivisionLoading) {
                    task.id?.let { taskId ->
                        onDivideSubtasks(taskId)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isSubtaskDivisionLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Subtask Division",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable {
                    // Create a new subtask at the end of the list
                    task.id?.let { parentId ->
                        onAddSubtask(parentId, "", null)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Add subtask",
                tint = MaterialTheme.colorScheme.onSecondary
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
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = "Set reminder",
                tint = MaterialTheme.colorScheme.onTertiary
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "Priority: ${task.priority}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Red Delete Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = Color(0xFFE53935), // Red color
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable {
                    onDeleteTask(task)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete task",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun SubtasksSection(
    subtasks: List<Task>,
    onToggleSubtask: (String, Boolean) -> Unit,
    onEditSubtaskTitle: (String, String) -> Unit,
    onAddSubtask: (Long, String, Long?) -> Unit,
    onDeleteSubtask: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Create FocusRequesters for each subtask
    val focusRequesters = remember(subtasks.size) {
        List(subtasks.size) { FocusRequester() }
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Text(
            text = "Subtasks (${subtasks.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Subtasks list
        subtasks.forEachIndexed { index, subtask ->
            var subtaskTitle by remember { mutableStateOf(subtask.title) }
            
            // Update local state when subtask.title changes
            LaunchedEffect(subtask.title) {
                subtaskTitle = subtask.title
            }
            
            // Auto-focus on newly created subtask (empty title)
            LaunchedEffect(subtask.title, subtasks.size) {
                if (subtask.title.isEmpty() && index < focusRequesters.size) {
                    focusRequesters[index].requestFocus()
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val subtaskCheckboxColors = CheckboxDefaults.colors(
                    checkedColor = when (subtask.priority) {
                        Priority.HIGH -> Color(0xFFE53935)     // red
                        Priority.MEDIUM -> Color(0xFFFFA000)   // amber
                        Priority.LOW -> Color(0xFF43A047)      // green
                        Priority.DEFAULT -> MaterialTheme.colorScheme.primary
                    },
                    uncheckedColor = when (subtask.priority) {
                        Priority.HIGH -> Color(0xFFE53935)     // red
                        Priority.MEDIUM -> Color(0xFFFFA000)   // amber
                        Priority.LOW -> Color(0xFF43A047)      // green
                        Priority.DEFAULT -> MaterialTheme.colorScheme.onSurface
                    },
                    checkmarkColor = Color.White
                )
                
                Checkbox(
                    checked = subtask.status == TaskStatus.DONE,
                    onCheckedChange = { checked -> 
                        subtask.id?.let { onToggleSubtask(it.toString(), checked) }
                    },
                    colors = subtaskCheckboxColors
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                TextField(
                    value = subtaskTitle,
                    onValueChange = { newTitle ->
                        subtaskTitle = newTitle
                        subtask.id?.let { 
                            onEditSubtaskTitle(it.toString(), newTitle)
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = if (subtask.status == TaskStatus.DONE) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (subtask.status == TaskStatus.DONE) 
                            TextDecoration.LineThrough 
                        else null
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            // 创建新的子任务，插入到当前subtask的下一个位置
                            subtask.parentId?.let { parentId ->
                                onAddSubtask(parentId, "", (index + 1).toLong())
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(if (index < focusRequesters.size) focusRequesters[index] else FocusRequester())
                        .onKeyEvent { keyEvent ->
                            // 监听删除键：当文本为空且按下删除键时，删除当前subtask
                            if (keyEvent.key == Key.Backspace && 
                                keyEvent.type == KeyEventType.KeyUp && 
                                subtaskTitle.isEmpty()) {
                                
                                // 将焦点转移到上一个subtask（如果存在）
                                if (index > 0 && index - 1 < focusRequesters.size) {
                                    focusRequesters[index - 1].requestFocus()
                                }
                                
                                // 删除当前subtask
                                subtask.id?.let { subtaskId ->
                                    onDeleteSubtask(subtaskId)
                                }
                                
                                true // 消费这个键盘事件
                            } else {
                                false // 不消费，让系统处理
                            }
                        }
                )
            }
        }
    }
}
