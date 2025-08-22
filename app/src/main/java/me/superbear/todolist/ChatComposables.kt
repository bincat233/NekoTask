package me.superbear.todolist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.superbear.todolist.R

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(start = 16.dp, bottom = 8.dp),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun CatDock(
    modifier: Modifier = Modifier,
    avatarResId: Int = R.drawable.ic_cat_dock,
    text: String = "Assistant",
    showText: Boolean = false,    // 控制显示形态
    size: Dp = 55.dp,             // Dock外圆或高度
    avatarScale: Float = 0.85f,     // 内部头像相对比例
) {
    if (showText) {
        // 胶囊 Dock：头像 + 文本
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = avatarResId),
                    contentDescription = "Assistant Avatar",
                    modifier = Modifier
                        .size(size * avatarScale)
                        .clip(CircleShape)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        // 圆形 Dock：只有头像
        val avatarSize = size * avatarScale
        Surface(
            modifier = modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = avatarResId),
                    contentDescription = "Assistant Avatar",
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
fun ChatInputBar(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }

    val dockSize = 55.dp
    val dockGap = 8.dp

    Box(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
    ) {
        // 头像：左上对齐
        CatDock(
            text = "Assistant",
            showText = false,
            size = dockSize,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // 输入区：底部对齐，给左侧头像让出空间；最小高度=头像尺寸
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = dockSize + dockGap) // 水平让位给头像
                .heightIn(min = dockSize),          // 垂直至少与头像等高
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sendBtnSize = 40.dp
            val sendBtnInset = 6.dp
            val reservedEnd = sendBtnSize + sendBtnInset * 2

            Box(modifier = Modifier.weight(1f)) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Tell AI what you want to do…") },
                    minLines = 1,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),       // ❌ 不再用 .padding(end = reservedEnd)
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    // ✅ 用 trailingIcon 预留文字区域，避免被覆盖
                    trailingIcon = { Spacer(Modifier.width(reservedEnd)) }
                )

                // ✅ 覆盖层与 TextField 同尺寸的参考系
                Box(Modifier.matchParentSize()) {
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSend(text)
                                text = ""
                            }
                        },
                        enabled = text.isNotBlank(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)               // 右下角
                            .padding(end = sendBtnInset, bottom = sendBtnInset)
                            .size(sendBtnSize)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
    }
}