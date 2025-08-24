package me.superbear.todolist.ui.main

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatListBulleted
// Intentionally avoid importing Icons.filled.List to prevent conflict with kotlin.collections.List
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import me.superbear.todolist.ChatInputBar
import me.superbear.todolist.Sender
import me.superbear.todolist.SpeechBubble
import me.superbear.todolist.Task
import me.superbear.todolist.MessageStatus
import me.superbear.todolist.Priority
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// 调试开关：
// 1) DEBUG_FORCE_FULLSCREEN  强制进入全屏聊天（便于快速验证模糊/遮罩与交互）
// 2) DEBUG_FORCE_NO_BLUR_FALLBACK  强制走“旧设备回退路径”（不使用模糊，用深色遮罩模拟）
private const val DEBUG_FORCE_NO_BLUR_FALLBACK = false
// 2) DEBUG_DISABLE_PEEK_TIMEOUT  关闭 Peek 模式消息自动消失（便于调试叠层布局）
// 3) DEBUG_PEEK_TIMEOUT_MS       覆盖 Peek 模式的自动消失时间（>0 生效；<=0 使用默认值）
private const val DEBUG_DISABLE_PEEK_TIMEOUT = false
private const val DEBUG_PEEK_TIMEOUT_MS: Long = -1L

// Main app modes - replaces scattered boolean states
sealed class AppMode {
    object Normal : AppMode()           // Normal task list view with peek chat
    object ManualAdd : AppMode()        // Manual task addition mode
    object ChatFullscreen : AppMode()   // Fullscreen chat mode
}

// Chat overlay modes
sealed class ChatOverlayMode {
    data class Peek(val autoDismissMs: Long = 8000L) : ChatOverlayMode()
    object Fullscreen : ChatOverlayMode()
}

// Helper function to determine current app mode
@Composable
private fun getCurrentAppMode(
    manualMode: Boolean,
    chatOverlayMode: String
): AppMode {
    return when {
        manualMode -> AppMode.ManualAdd
        chatOverlayMode == "fullscreen" -> AppMode.ChatFullscreen
        else -> AppMode.Normal
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val onEvent = viewModel::onEvent
    val localDensity = LocalDensity.current
    
    // Calculate current app mode
    val currentMode = getCurrentAppMode(state.manualMode, state.chatOverlayMode)

    BackHandler(enabled = currentMode == AppMode.ManualAdd) { onEvent(UiEvent.CloseManual) }
    BackHandler(enabled = currentMode == AppMode.ChatFullscreen) { onEvent(UiEvent.SetChatOverlayMode("peek")) }

    @Composable
    fun ChatOverlay(
        messages: List<me.superbear.todolist.ChatMessage>,
        imeVisible: Boolean,
        fabWidthDp: androidx.compose.ui.unit.Dp,
        mode: ChatOverlayMode,
        isPinned: (me.superbear.todolist.ChatMessage) -> Boolean = { false },
        onMessageTimeout: (String) -> Unit = {},
        modifier: Modifier = Modifier
){
        val localDensity = LocalDensity.current

        // Parent applies system insets once for all bubbles
        Box(
            modifier = modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
        ) {
            val inputBarHeight = 70.dp // 与 ChatInputBar 高度保持一致，作为底部基线

            // Background and click interceptor based on mode
            if (mode is ChatOverlayMode.Fullscreen) {
                val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !DEBUG_FORCE_NO_BLUR_FALLBACK
                if (!canBlur) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxSize().padding(bottom = inputBarHeight)
                    ) {}
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = inputBarHeight)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                )
            }

            when (mode) {
                is ChatOverlayMode.Fullscreen -> {
                    // --- Fullscreen Mode: Scrollable LazyColumn ---
                    val listState = rememberLazyListState()

                    // Auto-scroll to bottom when a new message arrives
                    LaunchedEffect(messages.size) {
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(0)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = inputBarHeight), // Prevent overlap with input bar
                        reverseLayout = true, // Lays out items from bottom to top
                        contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom)
                    ) {
                        items(messages.reversed()) { message ->
                            val isUser = message.sender == Sender.User
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                SpeechBubble(
                                    text = message.text,
                                    isUser = isUser
                                )
                            }
                        }
                    }
                }
                is ChatOverlayMode.Peek -> {
                    // --- Peek Mode: Original Box layout for precise positioning ---
                    val messageSpacing = 12.dp
                    val fabApproxHeight = 72.dp
                    val messageSizes = remember { mutableStateOf(mutableMapOf<String, IntSize>()) }

                    messages.forEachIndexed { index, message ->
                        val pinned = isPinned(message)
                        if (!pinned && !DEBUG_DISABLE_PEEK_TIMEOUT) {
                            val timeoutMs = if (DEBUG_PEEK_TIMEOUT_MS > 0) DEBUG_PEEK_TIMEOUT_MS else mode.autoDismissMs
                            LaunchedEffect(message.id, pinned) {
                                kotlinx.coroutines.delay(timeoutMs)
                                onMessageTimeout(message.id)
                            }
                        }

                        val isLast = index == messages.lastIndex
                        val fabIsVisible = !imeVisible
                        val avoidancePadding by animateDpAsState(
                            if (isLast && fabIsVisible) fabWidthDp + 32.dp else 0.dp,
                            label = "fabAvoidancePadding"
                        )

                        val bottomOffset = with(localDensity) {
                            var totalOffset = inputBarHeight.toPx()
                            for (i in (index + 1) until messages.size) {
                                val belowMessage = messages[i]
                                val belowSize = messageSizes.value[belowMessage.id]
                                val belowHeightPxRaw = (belowSize?.height?.toFloat() ?: 50.dp.toPx())

                                val isBelowLast = i == messages.lastIndex
                                val belowHeightPx = if (isBelowLast && fabIsVisible) {
                                    maxOf(belowHeightPxRaw, fabApproxHeight.toPx())
                                } else belowHeightPxRaw

                                totalOffset += belowHeightPx + messageSpacing.toPx()
                            }
                            totalOffset.toDp()
                        }

                        val isUser = message.sender == Sender.User
                        val align = if (isUser) Alignment.BottomEnd else Alignment.BottomStart

                        val bubbleModifier = Modifier
                            .align(align)
                            .padding(bottom = bottomOffset)
                            .padding(end = avoidancePadding)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { /* consume click */ }
                            .onGloballyPositioned { coordinates ->
                                val newSize = coordinates.size
                                if (messageSizes.value[message.id] != newSize) {
                                    messageSizes.value = messageSizes.value.toMutableMap().apply {
                                        put(message.id, newSize)
                                    }
                                }
                            }

                        SpeechBubble(
                            text = message.text,
                            isUser = isUser,
                            modifier = bubbleModifier
                        )
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // canBlur：Android 12+(RenderEffect) 且未强制回退时启用真实模糊
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !DEBUG_FORCE_NO_BLUR_FALLBACK
        // 模糊半径动画：全屏聊天时过渡到 16.dp，否则为 0
        val blurRadius by animateDpAsState(
            targetValue = if (currentMode == AppMode.ChatFullscreen && canBlur) 16.dp else 0.dp,
            label = "blurAnimation"
        )

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (currentMode != AppMode.ManualAdd) {
                    ChatInputBar(
                        onSend = { onEvent(UiEvent.SendChat(it)) },
                        onDockClick = { onEvent(UiEvent.EnterFullscreenChat) },
                        modifier = Modifier
                            .navigationBarsPadding()// ← 没弹键盘时让出导航条
                            .imePadding()           // ← 弹出键盘时让出 IME
                    )
                }
            },
            floatingActionButton = {
                // Show FAB only in normal mode when keyboard is not visible
                if (currentMode == AppMode.Normal && !state.imeVisible) {
                    ManualAddFab(
                        onClick = { onEvent(UiEvent.OpenManual) },
                        modifier = Modifier
                            .onSizeChanged {
                                onEvent(UiEvent.FabMeasured(with(localDensity) { it.width.toDp() }))
                            }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            // 仅对“主内容区域”应用模糊，不影响底部 ChatInputBar（输入区始终清晰可交互）
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .blur(radius = blurRadius)
            ) {
                val unfinishedItems = state.items.filter { it.status == "OPEN" }
                val finishedItems = state.items.filter { it.status == "DONE" }

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = "Unfinished",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    val openParents = unfinishedItems.filter { it.parentId == null }
                    items(openParents) { parent ->
                        ParentTaskCard(
                            task = parent,
                            viewModel = viewModel,
                            onToggleParent = { onEvent(UiEvent.ToggleTask(parent)) }
                        )
                    }
                    item {
                        Text(
                            text = "Finished",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    val doneParents = finishedItems.filter { it.parentId == null }
                    items(doneParents) { parent ->
                        ParentTaskCard(
                            task = parent,
                            viewModel = viewModel,
                            onToggleParent = { onEvent(UiEvent.ToggleTask(parent)) }
                        )
                    }
                }
            }
        }

        // Dynamic ChatOverlay based on app mode
        val overlayMode = when (currentMode) {
            AppMode.ChatFullscreen -> ChatOverlayMode.Fullscreen
            else -> ChatOverlayMode.Peek()
        }
        val overlayMessages = when (currentMode) {
            AppMode.ChatFullscreen -> state.messages
            else -> state.peekMessages
        }

        // 仅在“等待回复”期间，pin 对应的那一条用户消息：
        // 策略：
        // 1) 找到最新的一条 Assistant 且 status==Sending 的消息 idxA
        // 2) 从 idxA 往前找到最近的一条 User 消息，记为 pendingUserMessageId

        ChatOverlay(
            messages = overlayMessages,
            imeVisible = state.imeVisible,
            fabWidthDp = state.fabWidthDp,
            mode = overlayMode,
            isPinned = { message ->
                // Manual pin
                message.id in state.pinnedMessageIds ||
                // Auto-pin non-sent messages
                message.status != MessageStatus.Sent ||
                // Auto-pin user messages that have pending assistant replies (based on replyToId association)
                (message.sender == Sender.User && 
                 overlayMessages.any { it.sender == Sender.Assistant && it.status == MessageStatus.Sending && it.replyToId == message.id })
            },
            onMessageTimeout = { id -> onEvent(UiEvent.DismissPeekMessage(id)) }
        )

        if (currentMode == AppMode.ManualAdd) {
            Scrim(onDismiss = { onEvent(UiEvent.CloseManual) }, modifier = Modifier.fillMaxSize())
            ManualAddCard(
                title = state.manualTitle,
                onTitleChange = { onEvent(UiEvent.ChangeTitle(it)) },
                description = state.manualDesc,
                onDescriptionChange = { onEvent(UiEvent.ChangeDesc(it)) },
                onSend = { onEvent(UiEvent.ManualAddSubmit(state.manualTitle, state.manualDesc)) },
                onCancel = { onEvent(UiEvent.CloseManual) },
                onDueDateClick = { onEvent(UiEvent.OpenDateTimePicker) },
                selectedDueDate = state.selectedDueDate,
                onPriorityClick = { onEvent(UiEvent.OpenPriorityMenu) },
                showPriorityMenu = state.showPriorityMenu,
                selectedPriority = state.selectedPriority,
                onPrioritySelected = { p ->
                    onEvent(UiEvent.SetPriority(p))
                    onEvent(UiEvent.ClosePriorityMenu)
                },
                onPriorityDismiss = { onEvent(UiEvent.ClosePriorityMenu) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        
        // Date Time Picker
        if (state.showDateTimePicker) {
            DateTimePickerCard(
                selectedDate = state.selectedDueDate,
                onDateTimeSelected = { timestamp ->
                    onEvent(UiEvent.SetDueDate(timestamp))
                    onEvent(UiEvent.CloseDateTimePicker)
                },
                onCancel = { onEvent(UiEvent.CloseDateTimePicker) }
            )
        }
    }
}

@Composable
private fun ParentTaskCard(
    task: Task,
    viewModel: MainViewModel,
    onToggleParent: () -> Unit
) {
    var expanded by remember(task.id) { mutableStateOf(false) }
    var showAddDialog by remember(task.id) { mutableStateOf(false) }
    val children = remember(task.id, viewModel.uiState.collectAsState().value.items) {
        viewModel.getChildren(task.id)
    }
    val (doneCount, totalCount) = remember(task.id, viewModel.uiState.collectAsState().value.items) {
        viewModel.getParentProgress(task.id)
    }

    // Auto-expand when first subtask created
    LaunchedEffect(totalCount) {
        if (totalCount == 1) expanded = true
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.status == "DONE",
                onCheckedChange = { onToggleParent() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (task.status == "DONE") Color.Gray else Color.Unspecified,
                        textDecoration = if (task.status == "DONE") TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f)
                    )
                    // Progress badge X/Y
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = "$doneCount/$totalCount",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                }
                // Due today indicator (minimal)
                val dueToday = remember(task.dueAt) { isDueToday(task) }
                if (dueToday) {
                    Text(
                        text = "Due today",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                children.forEach { child ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = child.status == "DONE",
                            onCheckedChange = { checked -> viewModel.toggleSubtaskDone(child.id, checked) }
                        )
                        Text(
                            text = child.title,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                            color = if (child.status == "DONE") Color.Gray else Color.Unspecified,
                            textDecoration = if (child.status == "DONE") TextDecoration.LineThrough else null
                        )
                    }
                }

                TextButton(onClick = { showAddDialog = true }, modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = "+ Subtask")
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) {
                        viewModel.addSubtask(task.id, title)
                        title = ""
                        showAddDialog = false
                        expanded = true
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } },
            title = { Text("New subtask") },
            text = {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    placeholder = { Text("Subtask title") }
                )
            }
        )
    }
}

private fun isDueToday(task: Task): Boolean {
    val due = task.dueAt ?: return false
    val now = Clock.System.now()
    val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val dueDate = due.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return dueDate == today
}

@Composable
fun TaskItem(task: Task, onToggle: (Task) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(task) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.status == "DONE",
            onCheckedChange = { onToggle(task) }
        )
        Text(
            text = task.title,
            modifier = Modifier.padding(start = 8.dp),
            color = if (task.status == "DONE") Color.Gray else Color.Unspecified,
            textDecoration = if (task.status == "DONE") TextDecoration.LineThrough else null
        )
    }
}

@Composable
fun ManualAddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(Icons.Default.Add, contentDescription = "Add Task") },
        text = { Text("Add") }
    )
}

@Composable
fun ManualAddCard(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onDueDateClick: () -> Unit,
    selectedDueDate: Long?,
    onPriorityClick: () -> Unit,
    showPriorityMenu: Boolean,
    selectedPriority: Priority,
    onPrioritySelected: (Priority) -> Unit,
    onPriorityDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 8.dp,
            modifier = modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()// ← 没弹键盘时让出导航条
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Manual Add Card (TEST)")
                TextField(
                    value = title,
                    onValueChange = onTitleChange,
                    placeholder = { Text("What would you like to do?") },
                    textStyle = MaterialTheme.typography.titleMedium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.padding(8.dp))
                TextField(
                    value = description,
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
                            tint = if (selectedDueDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Priority selector
                    Box {
                        val priorityTint = when (selectedPriority) {
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
                            expanded = showPriorityMenu,
                            onDismissRequest = onPriorityDismiss
                        ) {
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFE53935)); Spacer(Modifier.width(8.dp)); Text("High") } },
                                onClick = { onPrioritySelected(Priority.HIGH) }
                            )
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFFFA000)); Spacer(Modifier.width(8.dp)); Text("Medium") } },
                                onClick = { onPrioritySelected(Priority.MEDIUM) }
                            )
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFF43A047)); Spacer(Modifier.width(8.dp)); Text("Low") } },
                                onClick = { onPrioritySelected(Priority.LOW) }
                            )
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(8.dp)); Text("None") } },
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
                    Button(onClick = onSend, enabled = title.isNotBlank()) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
fun Scrim(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize()
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
fun TaskItemPreview() {
    MaterialTheme {
        TaskItem(
            task = Task(
                id = 1,
                title = "Sample Task",
                notes = "This is a sample task for preview",
                status = "OPEN",
                priority = me.superbear.todolist.Priority.MEDIUM,
                createdAt = kotlinx.datetime.Clock.System.now()
            ),
            onToggle = { }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SpeechBubblePreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 用户消息 - 右对齐
            SpeechBubble(
                text = "Add a new task for tomorrow",
                isUser = true,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            
            // AI 消息 - 左对齐，稍微向下偏移
            SpeechBubble(
                text = "Hello! I can help you manage your tasks.",
                isUser = false,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 60.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerCard(
    selectedDate: Long?,
    onDateTimeSelected: (Long) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(true) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf(selectedDate ?: System.currentTimeMillis()) }
    
    val calendar = Calendar.getInstance().apply {
        timeInMillis = selectedDateMillis
    }
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis
        )
        
        DatePickerDialog(
            onDismissRequest = onCancel,
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMillis ->
                            selectedDateMillis = dateMillis
                            calendar.timeInMillis = dateMillis
                            showDatePicker = false
                            showTimePicker = true
                        }
                    }
                ) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE)
        )
        
        AlertDialog(
            onDismissRequest = onCancel,
            confirmButton = {
                TextButton(
                    onClick = {
                        calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        calendar.set(Calendar.MINUTE, timePickerState.minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        onDateTimeSelected(calendar.timeInMillis)
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}