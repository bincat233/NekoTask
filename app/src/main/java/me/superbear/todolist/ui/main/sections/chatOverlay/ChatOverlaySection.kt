package me.superbear.todolist.ui.main.sections.chatOverlay

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.superbear.todolist.BuildConfig
import me.superbear.todolist.domain.entities.ChatMessage
import me.superbear.todolist.domain.entities.MessageStatus
import me.superbear.todolist.domain.entities.Sender
import me.superbear.todolist.ui.main.sections.chatOverlay.FullscreenMessageList
import me.superbear.todolist.ui.main.sections.chatOverlay.PeekMessageList

 // Debug constants has been moved to BuildConfig: DEBUG_DISABLE_PEEK_TIMEOUT, DEBUG_PEEK_TIMEOUT_MS

// Chat overlay modes
sealed class ChatOverlayMode {
    data class Peek(val autoDismissMs: Long = 8000L) : ChatOverlayMode()
    object Fullscreen : ChatOverlayMode()
}

/**
 * Chat overlay section - handles peek/fullscreen UI, message list, FAB width, IME-aware layout
 */
@Composable
fun ChatOverlaySection(
    messages: List<ChatMessage>,
    imeVisible: Boolean,
    fabWidthDp: Dp,
    mode: ChatOverlayMode,
    isPinned: (ChatMessage) -> Boolean = { false },
    onMessageTimeout: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val localDensity = LocalDensity.current

    // Parent applies system insets once for all bubbles
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) {
        val inputBarHeight = 70.dp // Match ChatInputBar height

        // Background and click interceptor based on mode
        if (mode is ChatOverlayMode.Fullscreen) {
            val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !BuildConfig.DEBUG_FORCE_NO_BLUR_FALLBACK
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
                FullscreenChatLayout(
                    messages = messages,
                    inputBarHeight = inputBarHeight
                )
            }
            is ChatOverlayMode.Peek -> {
                PeekChatLayout(
                    messages = messages,
                    imeVisible = imeVisible,
                    fabWidthDp = fabWidthDp,
                    inputBarHeight = inputBarHeight,
                    autoDismissMs = mode.autoDismissMs,
                    isPinned = isPinned,
                    onMessageTimeout = onMessageTimeout,
                    localDensity = localDensity
                )
            }
        }
    }
}

@Composable
private fun FullscreenChatLayout(
    messages: List<ChatMessage>,
    inputBarHeight: Dp
) {
    FullscreenMessageList(
        messages = messages,
        inputBarHeight = inputBarHeight
    )
}

@Composable
private fun PeekChatLayout(
    messages: List<ChatMessage>,
    imeVisible: Boolean,
    fabWidthDp: Dp,
    inputBarHeight: Dp,
    autoDismissMs: Long,
    isPinned: (ChatMessage) -> Boolean,
    onMessageTimeout: (String) -> Unit,
    localDensity: androidx.compose.ui.unit.Density
) {
    // Apply debug timeout adjustments
    val adjustedAutoDismissMs = if (BuildConfig.DEBUG_DISABLE_PEEK_TIMEOUT) {
        Long.MAX_VALUE
    } else if (BuildConfig.DEBUG_PEEK_TIMEOUT_MS > 0) {
        BuildConfig.DEBUG_PEEK_TIMEOUT_MS
    } else {
        autoDismissMs
    }

    PeekMessageList(
        messages = messages,
        imeVisible = imeVisible,
        fabWidthDp = fabWidthDp,
        inputBarHeight = inputBarHeight,
        autoDismissMs = adjustedAutoDismissMs,
        isPinned = isPinned,
        onMessageTimeout = onMessageTimeout
    )
}
