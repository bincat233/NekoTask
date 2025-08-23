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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.IntSize.Companion
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import me.superbear.todolist.ChatInputBar
import me.superbear.todolist.Sender
import me.superbear.todolist.SpeechBubble
import me.superbear.todolist.Task
import me.superbear.todolist.MessageStatus

// Temporary debug flag: force fullscreen mode
private const val DEBUG_FORCE_FULLSCREEN = true
private const val DEBUG_FORCE_NO_BLUR_FALLBACK = true

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
    chatOverlayMode: String,
    debugForceFullscreen: Boolean = DEBUG_FORCE_FULLSCREEN
): AppMode {
    return when {
        manualMode -> AppMode.ManualAdd
        debugForceFullscreen || chatOverlayMode == "fullscreen" -> AppMode.ChatFullscreen
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
            val messageSpacing = 12.dp  // fixed spacing between messages
            val inputBarHeight = 70.dp  // baseline above input bar

            // Fullscreen mode background
            when (mode) {
                is ChatOverlayMode.Fullscreen -> {
                    // On modern devices, the background is blurred by the caller.
                    // On older devices, we add a fallback scrim.
                    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !DEBUG_FORCE_NO_BLUR_FALLBACK
                    if (!canBlur) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = inputBarHeight) // Don't cover input bar
                        ) {}
                    }

                    // Click interceptor to prevent interaction with blurred content behind
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = inputBarHeight) // Don't cover input bar
                            .clickable(indication = null, interactionSource = interaction) { }
                    )
                }
                is ChatOverlayMode.Peek -> {
                    // No background in peek mode
                }
            }

            // store each message's measured size
            val messageSizes = remember { mutableStateOf(mutableMapOf<String, IntSize>()) }

            messages.forEachIndexed { index, message ->
                // Auto-dismiss timeout for each message (unless pinned) - only in peek mode
                when (mode) {
                    is ChatOverlayMode.Peek -> {
                        val pinned = isPinned(message)
                        if (!pinned) {
                            LaunchedEffect(message.id, pinned) {
                                kotlinx.coroutines.delay(mode.autoDismissMs)
                                onMessageTimeout(message.id)
                            }
                        }
                    }
                    is ChatOverlayMode.Fullscreen -> {
                        // No timeout in fullscreen mode
                    }
                }

                val isLast = index == messages.lastIndex
                val fabIsVisible = mode is ChatOverlayMode.Peek && !imeVisible
                val avoidancePadding by animateDpAsState(
                    if (isLast && fabIsVisible) {
                        fabWidthDp + 32.dp // FAB width + 16dp margins on both sides
                    } else 0.dp,
                    label = "fabAvoidancePadding"
                )

                val bottomOffset = with(localDensity) {
                    var totalOffset = inputBarHeight.toPx()
                    for (i in (index + 1) until messages.size) {
                        val belowMessage = messages[i]
                        val belowSize = messageSizes.value[belowMessage.id]
                        val belowHeightPx = (belowSize?.height?.toFloat() ?: 50.dp.toPx())
                        totalOffset += belowHeightPx + messageSpacing.toPx()
                    }
                    totalOffset.toDp()
                }

                val isUser = message.sender == Sender.User
                val align = if (isUser) Alignment.BottomEnd else Alignment.BottomStart

                // Click handling based on mode
                val interaction = remember { MutableInteractionSource() }
                val clickModifier = when (mode) {
                    is ChatOverlayMode.Peek -> {
                        // In peek mode, bubbles consume clicks to prevent background interaction
                        Modifier.clickable(
                            indication = null,
                            interactionSource = interaction
                        ) { /* consume click */ }
                    }
                    is ChatOverlayMode.Fullscreen -> {
                        // In fullscreen mode, bubbles don't need to consume clicks
                        Modifier
                    }
                }

                val bubbleModifier = Modifier
                    .align(align)
                    .padding(bottom = bottomOffset)
                    .padding(end = avoidancePadding)
                    .then(clickModifier)
                    .onGloballyPositioned { coordinates ->
                        val newSize = coordinates.size
                        val oldSize = messageSizes.value[message.id]
                        if (oldSize != newSize) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !DEBUG_FORCE_NO_BLUR_FALLBACK
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
                // Auto-pin user messages when assistant is still processing
                (message.sender == Sender.User && 
                 state.peekMessages.any { it.sender == Sender.Assistant && it.status == MessageStatus.Sending })
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