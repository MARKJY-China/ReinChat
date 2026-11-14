package com.reinchat.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
    dotSize: Float = 4.2f,
) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1150, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "phase",
    )
    Canvas(modifier = modifier.size(width = 28.dp, height = 12.dp)) {
        val gap = size.width / 4f
        repeat(3) { index ->
            val wave = sin((phase * 2 * PI - index * 0.9).toFloat())
            val alpha = 0.22f + ((wave + 1f) / 2f) * 0.78f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = dotSize.dp.toPx() / 2f,
                center = Offset(gap * (index + 1), size.height / 2f),
            )
        }
    }
}

@Composable
fun LiquidOrb(
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    letter: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFF3FFF9),
                    0.18f to ReinColors.Mint,
                    0.55f to Color(0xFF77DCC8),
                    0.82f to Color(0xFF8BB8F8),
                    1f to ReinColors.Violet,
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = this.size.minDimension * 0.19f,
                center = Offset(this.size.width * 0.31f, this.size.height * 0.24f),
            )
            drawCircle(
                color = Color(0xFF06231C).copy(alpha = 0.17f),
                radius = this.size.minDimension * 0.38f,
                center = Offset(this.size.width * 0.80f, this.size.height * 0.84f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.17f),
                radius = this.size.minDimension * 0.21f,
                center = Offset(this.size.width * 0.68f, this.size.height * 0.35f),
            )
        }
        if (letter) {
            Text(
                text = "R",
                color = Color(0xFF06201A),
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
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
