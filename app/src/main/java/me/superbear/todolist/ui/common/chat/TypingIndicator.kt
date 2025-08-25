package me.superbear.todolist.ui.common.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A reusable typing indicator that mimics WhatsApp's three-dot animation.
 *
 * Each dot scales and fades in a staggered sequence to create a wave-like effect.
 * The animation runs as long as this composable is in the composition and stops automatically when removed.
 *
 * @param modifier Optional [Modifier] for layout customization.
 * @param dotSize The diameter of each dot.
 * @param dotSpacing The horizontal spacing between dots.
 * @param durationMillis The total duration of a full wave cycle.
 * @param color The base color of the dots.
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 4.dp,
    durationMillis: Int = 1200,
    color: Color = Color(0xFF8A8A8E),
) {
    val minScale = 0.6f
    val maxScale = 1.0f
    // Row layout for horizontal alignment
    Row(modifier = modifier) {
        repeat(3) { index ->
            val scale by dotScaleAnimation(
                index = index,
                totalDurationMillis = durationMillis,
                minScale = minScale,
                maxScale = maxScale,
            )

            val alpha = remember(scale) {
                val t = ((scale - minScale) / (maxScale - minScale)).coerceIn(0f, 1f)
                0.35f + 0.65f * t
            }

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .background(color = color, shape = CircleShape)
            )

            if (index < 2) {
                Spacer(modifier = Modifier.width(dotSpacing))
            }
        }
    }
}

@Composable
private fun dotScaleAnimation(
    index: Int,
    totalDurationMillis: Int,
    minScale: Float,
    maxScale: Float,
): androidx.compose.runtime.State<Float> {
    val transition: InfiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typingTransition")

    // Stagger each dot by a third of the full duration so they animate in sequence.
    val delayPerDot = remember(totalDurationMillis) { totalDurationMillis / 3 }
    val effectiveDelay = remember(index, delayPerDot) { index * delayPerDot }

    val currentMin by rememberUpdatedState(minScale)
    val currentMax by rememberUpdatedState(maxScale)

    return transition.animateFloat(
        initialValue = currentMin,
        targetValue = currentMin, // We use keyframes to control the values over time within the same cycle.
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = totalDurationMillis
                // Start small -> grow -> shrink within one cycle.
                currentMin at 0 using FastOutSlowInEasing
                currentMax at totalDurationMillis / 2 using FastOutSlowInEasing
                currentMin at totalDurationMillis using FastOutSlowInEasing
                delayMillis = effectiveDelay
            }
        ),
        label = "dotScale$index"
    )
}
