package me.superbear.todolist.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import me.superbear.todolist.ChatInputBar
import me.superbear.todolist.Sender
import me.superbear.todolist.SpeechBubble
import me.superbear.todolist.Task

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val onEvent = viewModel::onEvent
    val localDensity = LocalDensity.current

    BackHandler(enabled = state.manualMode) { onEvent(UiEvent.CloseManual) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!state.manualMode) {
                    ChatInputBar(
                        onSend = { onEvent(UiEvent.SendChat(it)) },
                        modifier = Modifier
                            .navigationBarsPadding()// ← 没弹键盘时让出导航条
                            .imePadding()           // ← 弹出键盘时让出 IME
                    )
                }
            },
            floatingActionButton = {
                if (!state.manualMode && !state.imeVisible) {
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
            val unfinishedItems = state.items.filter { it.status == "OPEN" }
            val finishedItems = state.items.filter { it.status == "DONE" }

            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                item {
                    Text(
                        text = "Unfinished",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(unfinishedItems) { item ->
                    TaskItem(task = item, onToggle = { onEvent(UiEvent.ToggleTask(item)) })
                }
                item {
                    Text(
                        text = "Finished",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(finishedItems) { item ->
                    TaskItem(task = item, onToggle = { onEvent(UiEvent.ToggleTask(item)) })
                }
            }
        }

        // 智能消息间距系统
        val messageSpacing = 12.dp  // 消息间的固定间距
        val inputBarHeight = 70.dp  // 输入栏高度
        
        // 存储每条消息的高度
        val messageSizes = remember { mutableStateOf(mutableMapOf<String, IntSize>()) }
        
        state.messages.forEachIndexed { index, message ->
            val isLast = index == state.messages.lastIndex
            val avoidancePadding by animateDpAsState(
                if (isLast && !state.imeVisible && !state.manualMode) {
                    // FAB宽度 + 左右各留16dp缓冲空间
                    state.fabWidthDp + 32.dp
                } else 0.dp,
                label = ""
            )
            
            // 计算当前消息应该距离底部的距离
            val bottomOffset = with(localDensity) {
                var totalOffset = inputBarHeight.toPx()
                
                // 累加下方所有消息的高度 + 间距
                for (i in (index + 1) until state.messages.size) {
                    val belowMessage = state.messages[i]
                    val belowSize = messageSizes.value[belowMessage.id]
                    if (belowSize != null) {
                        totalOffset += belowSize.height + messageSpacing.toPx()
                    } else {
                        // 如果还没测量到高度，使用估算值
                        totalOffset += 50.dp.toPx() + messageSpacing.toPx()
                    }
                }
                
                totalOffset.toDp()
            }

            val isUser = message.sender == Sender.User
            val align = if (isUser) Alignment.BottomEnd else Alignment.BottomStart

            val bubbleModifier = Modifier
                .align(align)
                .padding(bottom = bottomOffset)
                .padding(end = avoidancePadding)
                .imePadding()
                .onGloballyPositioned { coordinates ->
                    messageSizes.value = messageSizes.value.toMutableMap().apply {
                        put(message.id, coordinates.size)
                    }
                }

            SpeechBubble(
                text = message.text,
                isUser = isUser,
                modifier = bubbleModifier
            )
        }

        if (state.manualMode) {
            Scrim(onDismiss = { onEvent(UiEvent.CloseManual) }, modifier = Modifier.fillMaxSize())
            ManualAddCard(
                title = state.manualTitle,
                onTitleChange = { onEvent(UiEvent.ChangeTitle(it)) },
                description = state.manualDesc,
                onDescriptionChange = { onEvent(UiEvent.ChangeDesc(it)) },
                onSend = { onEvent(UiEvent.ManualAddSubmit(state.manualTitle, state.manualDesc)) },
                onCancel = { onEvent(UiEvent.CloseManual) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
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
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Default.Event, contentDescription = "Due date")
                    }
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Default.Flag, contentDescription = "Priority")
                    }
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Default.Folder, contentDescription = "Project")
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