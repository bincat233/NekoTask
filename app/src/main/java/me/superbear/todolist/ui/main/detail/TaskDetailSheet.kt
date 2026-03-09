package me.superbear.todolist.ui.main.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import me.superbear.todolist.domain.entities.Priority
import me.superbear.todolist.domain.entities.Task
import me.superbear.todolist.domain.entities.TaskStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = visible) {
        coroutineScope.launch {
            if (sheetState.currentValue == SheetValue.Expanded) {
                sheetState.partialExpand()
            } else {
                sheetState.hide()
                onDismiss()
            }
        }
    }

    if (visible && task != null) {
        // ModalBottomSheet：Material3 原生底部弹窗，自带拖拽手柄、遮罩、动画。
        // 内容 lambda 是一个 ColumnScope，子元素从上到下排列，高度受弹窗面板约束。
        // 面板高度：半屏时 ≈ 50% 屏幕，全屏时 ≈ 90% 屏幕。
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier,
        ) {
            // ── 区域 A：可滚动内容区 ──────────────────────────────────
            // weight(1f, fill=false)：在父 ColumnScope（面板高度有界）中
            //   占据除 Toolbar 之外的剩余空间，让 Toolbar 始终贴底。
            // fill=false：内容不足时不强制撑满，面板自然收缩。
            // verticalScroll：内容超出面板高度时可向上滚动。
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 顶部信息行：完成复选框 + 截止日期 + 优先级旗标
                HeaderSection(
                    task = task,
                    onToggleDone = onToggleDone,
                    onChangeDue = onChangeDue,
                    onChangePriority = onChangePriority
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 任务标题输入框（单行）
                TitleSection(
                    title = title,
                    onTitleChange = onTitleChange
                )

                if (subtasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    // 子任务列表（可逐行编辑）
                    SubtasksSection(
                        subtasks = subtasks,
                        onToggleSubtask = onToggleSubtask,
                        onEditSubtaskTitle = onEditSubtaskTitle,
                        onAddSubtask = onAddSubtask,
                        onDeleteSubtask = onDeleteSubtask
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 任务详情/备注输入框（多行）
                ContentSection(
                    content = content,
                    onContentChange = onContentChange
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── 区域 B：操作工具栏（固定在面板底部）────────────────────
            // 位于可滚动区域之外，不会随内容滚动而消失。
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
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
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

    // Row：水平排列三个子元素 —— [复选框] [截止日期(weight=1)] [优先级旗标]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 左：完成状态复选框，颜色跟随优先级
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

        // 中：截止日期 chip，weight(1f) 让它撑满复选框与旗标之间的空间
        DueChip(
            dueAt = task.dueAt,
            onClick = { onChangeDue(task) },
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 右：优先级旗标 + 下拉菜单
        // Box 作为锚点：DropdownMenu 会相对于这个 Box 定位弹出
        Box {
            PriorityFlag(
                priority = task.priority,
                onClick = { showPriorityMenu = true }
            )

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
    // Box 作为 DropdownMenu 的父容器；DropdownMenu 会弹出在 Box 范围内
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
        colors = TextFieldDefaults.colors(
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

/**
 * Renders multiline content editor with transparent styling and focus handling
 */
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
            colors = TextFieldDefaults.colors(
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


/**
 * Composable function for rendering a toolbar with various task management actions.
 *
 * @param task The task associated with the toolbar. Contains data such as ID, title, priority, and more.
 * @param onToggleDone Callback to toggle the task's completion status. Invoked with the updated task and status.
 * @param onChangeDue Callback to modify the due date of the task.
 * @param onChangePriority Callback to update the task's priority. Invoked with the task and the new priority value.
 * @param onAddSubtask Callback to add a new subtask. Invoked with the parent task ID, new subtask title, and order in the parent (optional).
 * @param onDivideSubtasks Callback to divide the task into subtasks using AI. Invoked with the task ID.
 * @param onDeleteTask Callback to delete the task. Invoked with the task to be deleted.
 * @param isSubtaskDivisionLoading Boolean indicating whether the AI subtask division process is currently loading.
 * @param modifier Modifier for customizing the layout or appearance of the toolbar.
 */
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

    // Row：水平排列所有按钮
    //   [AI拆分] [添加子任务] [提醒] ── Spacer(weight=1,撑开) ── [优先级文字] [删除]
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Box：用来叠加内容（图标 或 加载圈），并设置背景色和圆角
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

        // Add subtask button
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

        // Set Reminder Button
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
        // Section header "Subtasks (2)"
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
