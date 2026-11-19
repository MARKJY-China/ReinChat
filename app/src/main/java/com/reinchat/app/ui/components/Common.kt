package com.reinchat.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reinchat.app.ui.theme.ReinColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ThinkingDots(
    modifier: Modifier = Modifier,
    color: Color = ReinColors.Mint,
    dotSize: Float = 3.6f,
) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "phase",
    )
    Canvas(modifier = modifier.size(width = 22.dp, height = 10.dp)) {
        val gap = size.width / 4f
        repeat(3) { index ->
            val wave = sin((phase * 2 * PI - index * 0.9).toFloat())
            val alpha = 0.24f + ((wave + 1f) / 2f) * 0.66f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = dotSize.dp.toPx() / 2f,
                center = Offset(gap * (index + 1), size.height / 2f),
            )
        }
    }
}

/**
 * Flat brand mark. Kept under the old name because several screens referenced it;
 * it is now an ink plate with the paper-coloured "R", matching the web wordmark.
 */
@Composable
fun LiquidOrb(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    letter: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(ReinColors.TextPrimary),
        contentAlignment = Alignment.Center,
    ) {
        if (letter) {
            Text(
                text = "R",
                color = ReinColors.OnInk,
                fontSize = (size.value * 0.44f).sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(ReinColors.OnInk.copy(alpha = 0.9f))
            )
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val dayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

fun formatClock(timestamp: Long): String {
    return timeFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}

fun formatSessionTime(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        date == today -> timeFormatter.format(Instant.ofEpochMilli(timestamp).atZone(zone))
        date == today.minusDays(1) -> "昨天"
        date.isAfter(today.minusDays(7)) -> dayFormatter.format(date)
        date.year == today.year -> dayFormatter.format(date)
        else -> DateTimeFormatter.ofPattern("yyyy/M/d", Locale.CHINA).format(date)
    }
}
