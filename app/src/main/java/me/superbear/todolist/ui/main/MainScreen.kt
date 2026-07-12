package me.superbear.todolist.ui.main

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import me.superbear.todolist.R
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.ui.common.chat.ChatInputBar
import me.superbear.todolist.domain.entities.MessageStatus
import me.superbear.todolist.domain.entities.Sender
import me.superbear.todolist.ui.main.sections.appShell.AppMode
import me.superbear.todolist.ui.main.sections.appShell.AppPage
import me.superbear.todolist.ui.main.sections.appShell.resolveBackAction
import me.superbear.todolist.ui.main.sections.appShell.resolveAppMode
import me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlayMode
import me.superbear.todolist.ui.main.sections.chatOverlay.ChatOverlaySection
import me.superbear.todolist.ui.main.sections.manualAddSuite.DateTimePickerSection
import me.superbear.todolist.ui.main.sections.manualAddSuite.ManualAddSection
import me.superbear.todolist.ui.main.detail.TaskDetailSheet
import me.superbear.todolist.ui.main.sections.tasks.TaskSection
import me.superbear.todolist.ui.main.state.MainScreenIntent
import me.superbear.todolist.ui.settings.ProviderDisplayInfo
import me.superbear.todolist.ui.settings.SettingsScreen
import me.superbear.todolist.ui.settings.SettingsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
) {
    // 从 ViewModel 订阅 UI 状态（StateFlow -> Compose 状态）
    val state by viewModel.appState.collectAsState()
    val currentApiKey by viewModel.currentApiKey.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()
    val selectedSearchCapability by viewModel.selectedSearchCapability.collectAsState()
    val currentSearchApiKey by viewModel.currentSearchApiKey.collectAsState()
    val onEvent = viewModel::onEvent
    val localDensity = LocalDensity.current
    
    // 抽屉导航状态
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // 页面导航状态
    val currentPage = state.appShellState.currentPage
    
    // 计算当前模式（简化多处布尔状态判断）
    val currentMode = resolveAppMode(state.manualAddState.isOpen, state.chatOverlayState.chatOverlayMode)
    val backAction = resolveBackAction(currentPage, currentMode, state.taskDetailState.isVisible)

    // 物理返回键处理：在不同模式下拦截并派发相应事件
    BackHandler(enabled = backAction != null) {
        onEvent(MainScreenIntent.HandleBackPressed)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 全屏聊天时主内容做模糊动画（Android 12+），老版本回退为不模糊
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !BuildConfig.DEBUG_FORCE_NO_BLUR_FALLBACK
        val blurRadius by animateDpAsState(
            targetValue = if (currentMode == AppMode.ChatFullscreen && canBlur) 16.dp else 0.dp,
            label = "blurAnimation"
        )

        // 抽屉导航包装整个内容
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    // 抽屉头部
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    HorizontalDivider()
                    
                    // 抽屉菜单项
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Task, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_tasks)) },
                        selected = currentPage == AppPage.TaskList,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                            }
                            onEvent(MainScreenIntent.NavigateTo(AppPage.TaskList))
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.NoteAlt, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_notes)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                            }
                            // TODO: Notes
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_settings)) },
                        selected = currentPage == AppPage.Settings,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                            }
                            onEvent(MainScreenIntent.NavigateTo(AppPage.Settings))
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_about)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                            }
                            // TODO: Show about info
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { 
                            Text(stringResource(R.string.app_name)) 
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (drawerState.isClosed) {
                                            drawerState.open()
                                        } else {
                                            drawerState.close()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.open_menu)
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    // TODO: Show more options menu
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_options)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                // 底部聊天输入：仅在非“手动添加”模式展示
                if (currentMode != AppMode.ManualAdd) {
                    ChatInputBar(
                        onSend = { message ->
                            // 发送消息到聊天层
                            onEvent(MainScreenIntent.SendChatMessage(message))
                        },
                        onDockClick = { 
                            // 切换聊天模式：若已在全屏则返回 Peek，否则进入全屏
                            if (state.chatOverlayState.chatOverlayMode == "fullscreen") {
                                onEvent(MainScreenIntent.SetChatOverlayMode("peek"))
                            } else {
                                onEvent(MainScreenIntent.SetChatOverlayMode("fullscreen"))
                            }
                        },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                    )
                }
            },
            floatingActionButton = {
                // Show FAB only in task list page, normal mode when keyboard is not visible
                // 添加任务的 FAB：仅在任务列表页面、常规模式且键盘未弹出时显示
                if (currentPage == AppPage.TaskList && currentMode == AppMode.Normal && !state.chatOverlayState.imeVisible) {
                    ManualAddFab(
                        onClick = { 
                            // 打开手动添加面板
                            onEvent(MainScreenIntent.OpenManualAdd)
                        },
                        modifier = Modifier
                            .onSizeChanged {
                                // 记录 FAB 宽度（用于聊天“窥视”布局避让）
                                onEvent(MainScreenIntent.FabMeasured(
                                    with(localDensity) { it.width.toDp() }
                                ))
                            }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            // 主内容区域：根据当前页面显示不同内容
            when (currentPage) {
                AppPage.TaskList -> {
                    // 任务列表页面（可被模糊的区域）
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
                                onEvent(MainScreenIntent.ToggleTask(task))
                            },
                            onAddSubtask = { parentId, title ->
                                // 新增子任务
                                onEvent(MainScreenIntent.AddSubtask(parentId, title))
                            },
                            onToggleSubtask = { childId, done ->
                                // 切换子任务完成状态
                                onEvent(MainScreenIntent.ToggleSubtask(childId, done))
                            },
                            getChildren = viewModel::getChildren,
                            getParentProgress = viewModel::getParentProgress,
                            onTaskClick = { task ->
                                task.id?.let { id ->
                                    onEvent(MainScreenIntent.ShowTaskDetail(id))
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                AppPage.Settings -> {
                    // 设置页面
                    SettingsScreen(
                        onNavigateBack = {
                            onEvent(MainScreenIntent.NavigateTo(AppPage.TaskList))
                        },
                        settingsState = settingsState,
                        onSettingsChange = viewModel::updateSettings,
                        selectedProvider = selectedProvider,
                        onProviderSelect = viewModel::selectProvider,
                        apiKey = currentApiKey,
                        onApiKeySave = viewModel::setApiKey,
                        selectedModel = selectedModel,
                        availableModels = availableModels,
                        isLoadingModels = isLoadingModels,
                        onModelSelect = viewModel::selectModel,
                        onRefreshModels = viewModel::refreshModels,
                        providerInfo = viewModel.PROVIDER_INFO.mapValues { (_, info) ->
                            ProviderDisplayInfo(info.displayName, info.fallbackModels)
                        },
                        onAddMemory = { content, category, importance, isActive ->
                            onEvent(MainScreenIntent.AddMemory(content, category, importance, isActive))
                        },
                        onEditMemory = { memory, content, category, importance, isActive ->
                            onEvent(MainScreenIntent.EditMemory(memory, content, category, importance, isActive))
                        },
                        onDeleteMemory = { memory ->
                            onEvent(MainScreenIntent.DeleteMemory(memory))
                        },
                        onToggleMemoryActive = { memory, isActive ->
                            onEvent(MainScreenIntent.ToggleMemoryActive(memory, isActive))
                        },
                        onLanguageChange = viewModel::updateLanguage,
                        onResetSampleData = viewModel::resetSampleData,
                        selectedSearchCapability = selectedSearchCapability,
                        onSearchCapabilitySelect = viewModel::setSearchCapability,
                        searchApiKey = currentSearchApiKey,
                        onSearchApiKeySave = viewModel::setSearchApiKey
                    )
                }
            }
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
                onEvent(MainScreenIntent.DismissPeekMessage(id))
            }
        )

        // 手动添加任务浮层 + 半透明遮罩
        if (currentMode == AppMode.ManualAdd) {
            Scrim(
                onDismiss = { 
                    // 点击遮罩关闭
                    onEvent(MainScreenIntent.CloseManualAdd)
                },
                modifier = Modifier.fillMaxSize()
            )
            ManualAddSection(
                state = state.manualAddState,
                dateTimePickerState = state.dateTimePickerState,
                priorityState = state.priorityState,
                onTitleChange = { title ->
                    // 标题输入
                    onEvent(MainScreenIntent.TypeManualAddTitle(title))
                },
                onDescriptionChange = { desc ->
                    // 描述输入
                    onEvent(MainScreenIntent.TypeManualAddDescription(desc))
                },
                onSubmit = {
                    // 提交创建任务
                    onEvent(MainScreenIntent.SubmitManualAdd)
                },
                onCancel = {
                    // 取消并关闭
                    onEvent(MainScreenIntent.CloseManualAdd)
                },
                onDueDateClick = {
                    // 打开日期时间选择器
                    onEvent(MainScreenIntent.OpenDateTimePicker(state.dateTimePickerState.selectedDueDateMs))
                },
                onPriorityClick = {
                    // 打开优先级菜单
                    onEvent(MainScreenIntent.OpenPriorityMenu)
                },
                onPrioritySelected = { priority ->
                    // 选择优先级
                    onEvent(MainScreenIntent.SetManualAddPriority(priority))
                },
                onPriorityDismiss = {
                    // 关闭优先级菜单
                    onEvent(MainScreenIntent.ClosePriorityMenu)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 任务详情底部弹窗
        TaskDetailSheet(
            visible = state.taskDetailState.isVisible,
            task = viewModel.getSelectedTask(),
            title = state.taskDetailState.editedTitle,
            onTitleChange = { newTitle ->
                onEvent(MainScreenIntent.EditTaskTitle(newTitle))
            },
            content = state.taskDetailState.editedContent,
            onContentChange = { newContent ->
                onEvent(MainScreenIntent.EditTaskContent(newContent))
            },
            subtasks = state.taskDetailState.selectedTaskId?.let { viewModel.getChildren(it) } ?: emptyList(),
            onToggleSubtask = { subtaskId, done ->
                onEvent(MainScreenIntent.ToggleSubtask(subtaskId.toLong(), done))
            },
            onEditSubtaskTitle = { subtaskId, newTitle ->
                onEvent(MainScreenIntent.EditSubtaskTitle(subtaskId.toLong(), newTitle))
            },
            onAddSubtask = { parentId, title, order ->
                onEvent(MainScreenIntent.AddSubtask(parentId, title, order))
            },
            onDeleteSubtask = { subtaskId ->
                onEvent(MainScreenIntent.DeleteSubtask(subtaskId))
            },
            onDismiss = {
                onEvent(MainScreenIntent.HideTaskDetail)
            },
            onToggleDone = { task, done ->
                // 切换任务完成状态
                onEvent(MainScreenIntent.ToggleTask(task))
            },
            onChangeDue = { task ->
                // 打开日期时间选择器
                onEvent(MainScreenIntent.OpenDateTimePicker(task.dueAt?.toEpochMilliseconds()))
            },
            onChangePriority = { task, priority ->
                // 更新任务优先级 (task.id为null表示是顶级task，使用selectedTaskId)
                val taskId = task.id ?: state.taskDetailState.selectedTaskId
                taskId?.let { id ->
                    onEvent(MainScreenIntent.UpdateTaskPriority(id, priority))
                }
            },
            onDivideSubtasks = { taskId ->
                // 使用设置页面配置的策略触发子任务划分
                onEvent(MainScreenIntent.DivideSubtasks(taskId))
            },
            onDeleteTask = { task ->
                // 删除任务 (task.id为null表示是顶级task，使用selectedTaskId)
                val taskId = task.id ?: state.taskDetailState.selectedTaskId
                taskId?.let { id ->
                    onEvent(MainScreenIntent.DeleteTask(id))
                }
            },
            isSubtaskDivisionLoading = state.subtaskDivisionState.isSubtaskDivisionLoading
        )

        
        // 日期时间选择器（全局显示，内部根据 state 控制可见性）
        DateTimePickerSection(
            state = state.dateTimePickerState,
            onDateTimeSelected = { timestamp ->
                // 设置截止时间并关闭
                onEvent(MainScreenIntent.SetDueDate(timestamp))
                // 自动关闭日期选择器
                onEvent(MainScreenIntent.CloseDateTimePicker)
            },
            onCancel = {
                // 关闭选择器
                onEvent(MainScreenIntent.CloseDateTimePicker)
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
        icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add)) },
        text = { Text(stringResource(R.string.add)) }
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
