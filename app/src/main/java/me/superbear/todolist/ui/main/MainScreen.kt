package me.superbear.todolist.ui.main

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.superbear.todolist.ui.common.chat.ChatInputBar
import me.superbear.todolist.domain.entities.MessageStatus
import me.superbear.todolist.domain.entities.Sender
import me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayMode
import me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlaySection
import me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerSection
import me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddSection
import me.superbear.todolist.ui.main.sections.tasks.TaskSection
import me.superbear.todolist.ui.main.state.AppEvent

// Debug constants
private const val DEBUG_FORCE_NO_BLUR_FALLBACK = false

// Main app modes - replaces scattered boolean states
sealed class AppMode {
    object Normal : AppMode()           // Normal task list view with peek chat
    object ManualAdd : AppMode()        // Manual task addition mode
    object ChatFullscreen : AppMode()   // Fullscreen chat mode
}

// Helper function to determine current app mode
@Composable
private fun getCurrentAppMode(
    manualAddOpen: Boolean,
    chatOverlayMode: String
): AppMode {
    return when {
        manualAddOpen -> AppMode.ManualAdd
        chatOverlayMode == "fullscreen" -> AppMode.ChatFullscreen
        else -> AppMode.Normal
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.appState.collectAsState()
    val onEvent = viewModel::onEvent
    val localDensity = LocalDensity.current
    
    // Calculate current app mode
    val currentMode = getCurrentAppMode(state.manualAddState.isOpen, state.chatOverlayState.chatOverlayMode)

    BackHandler(enabled = currentMode == AppMode.ManualAdd) { 
        onEvent(AppEvent.ManualAdd(me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.Close))
    }
    BackHandler(enabled = currentMode == AppMode.ChatFullscreen) { 
        onEvent(AppEvent.ChatOverlay(me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent.SetChatOverlayMode("peek")))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Blur animation for fullscreen chat
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
                        onSend = { message ->
                            onEvent(AppEvent.ChatOverlay(
                                me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent.SendMessage(message)
                            ))
                        },
                        onDockClick = { 
                            onEvent(AppEvent.ChatOverlay(
                                me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent.EnterFullscreenChat
                            ))
                        },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                    )
                }
            },
            floatingActionButton = {
                // Show FAB only in normal mode when keyboard is not visible
                if (currentMode == AppMode.Normal && !state.chatOverlayState.imeVisible) {
                    ManualAddFab(
                        onClick = { 
                            onEvent(AppEvent.ManualAdd(
                                me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.Open
                            ))
                        },
                        modifier = Modifier
                            .onSizeChanged {
                                onEvent(AppEvent.ChatOverlay(
                                    me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent.FabMeasured(
                                        with(localDensity) { it.width.toDp() }
                                    )
                                ))
                            }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            // Main content area with blur effect
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .blur(radius = blurRadius)
            ) {
                TaskSection(
                    state = state.taskState,
                    onToggleTask = { task ->
                        onEvent(AppEvent.Task(
                            me.superbear.todolist.ui.main.sections.tasks.TaskEvent.Toggle(task)
                        ))
                    },
                    onAddSubtask = { parentId, title ->
                        onEvent(AppEvent.Task(
                            me.superbear.todolist.ui.main.sections.tasks.TaskEvent.AddSubtask(parentId, title)
                        ))
                    },
                    onToggleSubtask = { childId, done ->
                        onEvent(AppEvent.Task(
                            me.superbear.todolist.ui.main.sections.tasks.TaskEvent.ToggleSubtask(childId, done)
                        ))
                    },
                    getChildren = viewModel::getChildren,
                    getParentProgress = viewModel::getParentProgress,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Chat Overlay
        val overlayMode = when (currentMode) {
            AppMode.ChatFullscreen -> ChatOverlayMode.Fullscreen
            else -> ChatOverlayMode.Peek()
        }
        val overlayMessages = when (currentMode) {
            AppMode.ChatFullscreen -> state.chatOverlayState.messages
            else -> state.chatOverlayState.peekMessages
        }

        ChatOverlaySection(
            messages = overlayMessages,
            imeVisible = state.chatOverlayState.imeVisible,
            fabWidthDp = state.chatOverlayState.fabWidthDp,
            mode = overlayMode,
            isPinned = { message ->
                // Manual pin
                message.id in state.chatOverlayState.pinnedMessageIds ||
                // Auto-pin non-sent messages
                message.status != MessageStatus.Sent ||
                // Auto-pin user messages that have pending assistant replies
                (message.sender == Sender.User && 
                 overlayMessages.any { it.sender == Sender.Assistant && it.status == MessageStatus.Sending && it.replyToId == message.id })
            },
            onMessageTimeout = { id -> 
                onEvent(AppEvent.ChatOverlay(
                    me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent.DismissPeekMessage(id)
                ))
            }
        )

        // Manual Add Section
        if (currentMode == AppMode.ManualAdd) {
            Scrim(
                onDismiss = { 
                    onEvent(AppEvent.ManualAdd(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.Close
                    ))
                },
                modifier = Modifier.fillMaxSize()
            )
            ManualAddSection(
                state = state.manualAddState,
                dateTimePickerState = state.dateTimePickerState,
                priorityState = state.priorityState,
                onTitleChange = { title ->
                    onEvent(AppEvent.ManualAdd(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.ChangeTitle(title)
                    ))
                },
                onDescriptionChange = { desc ->
                    onEvent(AppEvent.ManualAdd(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.ChangeDescription(desc)
                    ))
                },
                onSubmit = {
                    onEvent(AppEvent.ManualAdd(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.Submit(
                            state.manualAddState.title,
                            state.manualAddState.description
                        )
                    ))
                },
                onCancel = {
                    onEvent(AppEvent.ManualAdd(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.Close
                    ))
                },
                onDueDateClick = {
                    onEvent(AppEvent.DateTimePicker(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent.Open
                    ))
                },
                onPriorityClick = {
                    onEvent(AppEvent.Priority(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent.OpenMenu
                    ))
                },
                onPrioritySelected = { priority ->
                    onEvent(AppEvent.Priority(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent.SetPriority(priority)
                    ))
                },
                onPriorityDismiss = {
                    onEvent(AppEvent.Priority(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent.CloseMenu
                    ))
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        
        // Date Time Picker
        DateTimePickerSection(
            state = state.dateTimePickerState,
            onDateTimeSelected = { timestamp ->
                onEvent(AppEvent.DateTimePicker(
                    me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent.SetDueDate(timestamp)
                ))
            },
            onCancel = {
                onEvent(AppEvent.DateTimePicker(
                    me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent.Close
                ))
            }
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
