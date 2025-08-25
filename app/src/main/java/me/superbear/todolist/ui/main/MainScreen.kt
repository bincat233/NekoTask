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
import me.superbear.todolist.ui.main.sections.tasks.TaskEvent as TaskSectionEvent
import me.superbear.todolist.ui.main.state.AppEvent

// Debug constants
private const val DEBUG_FORCE_NO_BLUR_FALLBACK = false

// Main app modes - replaces scattered boolean states
sealed class AppMode {
    // 常规模式：任务列表 + 底部聊天输入（顶部“窥视”消息）
    object Normal : AppMode()           // Normal task list view with peek chat
    // 手动添加任务模式：浮层表单 + 遮罩
    object ManualAdd : AppMode()        // Manual task addition mode
    // 聊天全屏模式：主内容模糊，显示全屏聊天
    object ChatFullscreen : AppMode()   // Fullscreen chat mode
}

// Helper function to determine current app mode
@Composable
private fun getCurrentAppMode(
    manualAddOpen: Boolean,
    chatOverlayMode: String
): AppMode {
    return when {
        // 优先级：手动添加 > 聊天全屏 > 常规
        manualAddOpen -> AppMode.ManualAdd
        chatOverlayMode == "fullscreen" -> AppMode.ChatFullscreen
        else -> AppMode.Normal
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    // 从 ViewModel 订阅 UI 状态（StateFlow -> Compose 状态）
    val state by viewModel.appState.collectAsState()
    val onEvent = viewModel::onEvent
    val localDensity = LocalDensity.current
    
    // 计算当前模式（简化多处布尔状态判断）
    val currentMode = getCurrentAppMode(state.manualAddState.isOpen, state.chatOverlayState.chatOverlayMode)

    // 物理返回键处理：在不同模式下拦截并派发相应事件
    BackHandler(enabled = currentMode == AppMode.ManualAdd) { 
        onEvent(AppEvent.ManualAdd(me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.Close))
    }
    BackHandler(enabled = currentMode == AppMode.ChatFullscreen) { 
        onEvent(AppEvent.ChatOverlay(me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent.SetChatOverlayMode("peek")))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 全屏聊天时主内容做模糊动画（Android 12+），老版本回退为不模糊
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !DEBUG_FORCE_NO_BLUR_FALLBACK
        val blurRadius by animateDpAsState(
            targetValue = if (currentMode == AppMode.ChatFullscreen && canBlur) 16.dp else 0.dp,
            label = "blurAnimation"
        )

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                // 底部聊天输入：仅在非“手动添加”模式展示
                if (currentMode != AppMode.ManualAdd) {
                    ChatInputBar(
                        onSend = { message ->
                            // 发送消息到聊天层
                            onEvent(AppEvent.ChatOverlay(
                                me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent.SendMessage(message)
                            ))
                        },
                        onDockClick = { 
                            // 切换聊天模式：若已在全屏则返回 Peek，否则进入全屏
                            if (state.chatOverlayState.chatOverlayMode == "fullscreen") {
                                onEvent(AppEvent.ChatOverlay(
                                    me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent.SetChatOverlayMode("peek")
                                ))
                            } else {
                                onEvent(AppEvent.ChatOverlay(
                                    me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent.EnterFullscreenChat
                                ))
                            }
                        },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                    )
                }
            },
            floatingActionButton = {
                // Show FAB only in normal mode when keyboard is not visible
                // 添加任务的 FAB：仅在常规模式且键盘未弹出时显示
                if (currentMode == AppMode.Normal && !state.chatOverlayState.imeVisible) {
                    ManualAddFab(
                        onClick = { 
                            // 打开手动添加面板
                            onEvent(AppEvent.ManualAdd(
                                me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.Open
                            ))
                        },
                        modifier = Modifier
                            .onSizeChanged {
                                // 记录 FAB 宽度（用于聊天“窥视”布局避让）
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
            // 主内容区域（可被模糊的区域）：任务列表
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .blur(radius = blurRadius)
            ) {
                TaskSection(
                    state = state.taskState,
                    onToggleTask = { task ->
                        // 切换任务完成状态
                        onEvent(AppEvent.Task(TaskSectionEvent.Toggle(task)))
                    },
                    onAddSubtask = { parentId, title ->
                        // 新增子任务
                        onEvent(AppEvent.Task(TaskSectionEvent.AddSubtask(parentId, title)))
                    },
                    onToggleSubtask = { childId, done ->
                        // 切换子任务完成状态
                        onEvent(AppEvent.Task(TaskSectionEvent.ToggleSubtask(childId, done)))
                    },
                    getChildren = viewModel::getChildren,
                    getParentProgress = viewModel::getParentProgress,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 聊天浮层（两种模式：Peek/Fullscreen）
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
                // 置顶规则：
                // 1) 手动置顶
                message.id in state.chatOverlayState.pinnedMessageIds ||
                // 2) 非 Sent 状态（如 Sending/Failed）自动置顶
                message.status != MessageStatus.Sent ||
                // 3) 用户消息且其有待发送/进行中的助手回复
                (message.sender == Sender.User && 
                 overlayMessages.any { it.sender == Sender.Assistant && it.status == MessageStatus.Sending && it.replyToId == message.id })
            },
            onMessageTimeout = { id -> 
                // Peek 消息的自动消退
                onEvent(AppEvent.ChatOverlay(
                    me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayEvent.DismissPeekMessage(id)
                ))
            }
        )

        // 手动添加任务浮层 + 半透明遮罩
        if (currentMode == AppMode.ManualAdd) {
            Scrim(
                onDismiss = { 
                    // 点击遮罩关闭
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
                    // 标题输入
                    onEvent(AppEvent.ManualAdd(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.ChangeTitle(title)
                    ))
                },
                onDescriptionChange = { desc ->
                    // 描述输入
                    onEvent(AppEvent.ManualAdd(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.ChangeDescription(desc)
                    ))
                },
                onSubmit = {
                    // 提交创建任务
                    onEvent(AppEvent.ManualAdd(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.Submit(
                            state.manualAddState.title,
                            state.manualAddState.description
                        )
                    ))
                },
                onCancel = {
                    // 取消并关闭
                    onEvent(AppEvent.ManualAdd(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddEvent.Close
                    ))
                },
                onDueDateClick = {
                    // 打开日期时间选择器
                    onEvent(AppEvent.DateTimePicker(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent.Open
                    ))
                },
                onPriorityClick = {
                    // 打开优先级菜单
                    onEvent(AppEvent.Priority(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent.OpenMenu
                    ))
                },
                onPrioritySelected = { priority ->
                    // 选择优先级
                    onEvent(AppEvent.Priority(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent.SetPriority(priority)
                    ))
                },
                onPriorityDismiss = {
                    // 关闭优先级菜单
                    onEvent(AppEvent.Priority(
                        me.superbear.todolist.ui.main.sections.manualAddSuite.PriorityEvent.CloseMenu
                    ))
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        
        // 日期时间选择器（全局显示，内部根据 state 控制可见性）
        DateTimePickerSection(
            state = state.dateTimePickerState,
            onDateTimeSelected = { timestamp ->
                // 设置截止时间并关闭
                onEvent(AppEvent.DateTimePicker(
                    me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent.SetDueDate(timestamp)
                ))
            },
            onCancel = {
                // 关闭选择器
                onEvent(AppEvent.DateTimePicker(
                    me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerEvent.Close
                ))
            }
        )
    }
}

@Composable
fun ManualAddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // “添加任务”悬浮按钮（FAB）
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(Icons.Default.Add, contentDescription = "Add Task") },
        text = { Text("Add") }
    )
}

@Composable
fun Scrim(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // 半透明遮罩层：点击空白区域触发 onDismiss
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
