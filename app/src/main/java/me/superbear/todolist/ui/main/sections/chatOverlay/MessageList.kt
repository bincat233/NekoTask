package me.superbear.todolist.ui.main.sections.chatOverlay

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import me.superbear.todolist.domain.entities.ChatMessage
import me.superbear.todolist.domain.entities.MessageStatus
import me.superbear.todolist.domain.entities.Sender
import me.superbear.todolist.ui.common.chat.ChatBubble

@Composable
fun FullscreenMessageList(
    messages: List<ChatMessage>,
    inputBarHeight: Dp,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when a new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    if (messages.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = inputBarHeight)
        ) {
            Text(
                text = "No messages yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
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
                    ChatBubble(
                        text = message.text,
                        isUser = isUser
                    )
                }
            }
        }
    }
}

@Composable
fun PeekMessageList(
    messages: List<ChatMessage>,
    imeVisible: Boolean,
    fabWidthDp: Dp,
    inputBarHeight: Dp,
    autoDismissMs: Long,
    isPinned: (ChatMessage) -> Boolean,
    onMessageTimeout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val localDensity = LocalDensity.current
    val messageSpacing = 12.dp
    val fabApproxHeight = 72.dp
    val messageSizes = remember { mutableStateOf(mutableMapOf<String, IntSize>()) }

    Box(modifier = modifier.fillMaxSize()) {
        messages.forEachIndexed { index, message ->
            val pinned = isPinned(message)
            if (!pinned) {
                LaunchedEffect(message.id, pinned) {
                    kotlinx.coroutines.delay(autoDismissMs)
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

            ChatBubble(
                text = message.text,
                isUser = isUser,
                modifier = bubbleModifier
            )
        }
    }
}

@Preview
@Composable
private fun FullscreenMessageListPreview() {
    val sampleMessages = listOf(
        ChatMessage(
            id = "1",
            text = "Hello! How can I help you today?",
            sender = Sender.Assistant,
            timestamp = Clock.System.now(),
            status = MessageStatus.Sent
        ),
        ChatMessage(
            id = "2", 
            text = "I need help organizing my tasks",
            sender = Sender.User,
            timestamp = Clock.System.now(),
            status = MessageStatus.Sent
        )
    )
    
    FullscreenMessageList(
        messages = sampleMessages,
        inputBarHeight = 70.dp
    )
}
