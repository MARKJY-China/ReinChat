package com.reinchat.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.reinchat.app.ui.theme.ReinColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Grain(val x: Float, val y: Float, val radius: Float, val alpha: Float)

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 42_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val grains = remember {
        val random = Random(37)
        List(190) {
            Grain(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = 0.4f + random.nextFloat() * 1.35f,
                alpha = 0.008f + random.nextFloat() * 0.028f,
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0A1412),
                    0.38f to Color(0xFF060B0A),
                    1f to Color(0xFF040706),
                )
            ),
    ) {
        val w = maxWidth
        val angle = drift * 2f * PI.toFloat()

        AuroraBlob(
            color = Color(0xFF24E7AE),
            alpha = 0.38f * intensity,
            modifier = Modifier
                .size(w * 1.48f)
                .align(Alignment.TopStart)
                .graphicsLayer {
                    translationX = -size.width * 0.31f + sin(angle) * 42.dp.toPx()
                    translationY = -size.height * 0.33f + cos(angle * 0.8f) * 30.dp.toPx()
                    scaleX = 0.92f + breath * 0.12f
                    scaleY = 0.92f + breath * 0.12f
                },
        )

        AuroraBlob(
            color = Color(0xFF2BA6FF),
            alpha = 0.29f * intensity,
            modifier = Modifier
                .size(w * 1.26f)
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationX = size.width * 0.34f + cos(angle * 0.9f) * 34.dp.toPx()
                    translationY = -size.height * 0.15f + sin(angle) * 46.dp.toPx()
                    scaleX = 0.88f + breath * 0.17f
                    scaleY = 0.88f + breath * 0.17f
                },
        )

        AuroraBlob(
            color = Color(0xFF9279FF),
            alpha = 0.26f * intensity,
            modifier = Modifier
                .size(w * 1.16f)
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    translationX = size.width * 0.36f + sin(angle * 1.2f) * 42.dp.toPx()
                    translationY = size.height * 0.36f + cos(angle) * 34.dp.toPx()
                    scaleX = 0.9f + breath * 0.13f
                    scaleY = 0.9f + breath * 0.13f
                },
        )

        AuroraBlob(
            color = Color(0xFF2DD9C9),
            alpha = 0.11f * intensity,
            modifier = Modifier
                .size(w * 0.76f)
                .align(Alignment.CenterEnd)
                .graphicsLayer {
                    translationX = size.width * 0.34f + cos(angle * 1.5f) * 24.dp.toPx()
                    translationY = size.height * 0.12f + sin(angle * 1.1f) * 30.dp.toPx()
                },
        )

        AuroraBlob(
            color = Color(0xFFFFB677),
            alpha = 0.10f * intensity,
            modifier = Modifier
                .size(w * 0.72f)
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = -size.width * 0.20f + cos(angle * 1.4f) * 28.dp.toPx()
                    translationY = size.height * 0.30f + sin(angle * 0.7f) * 24.dp.toPx()
                },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x16050A08),
                        0.28f to Color.Transparent,
                        0.75f to Color(0x08050A08),
                        1f to Color(0x55040A08),
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x5C030806)),
                    center = center,
                    radius = size.maxDimension * 0.9f,
                )
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            grains.forEach { grain ->
                drawCircle(
                    color = Color.White.copy(alpha = grain.alpha),
                    radius = grain.radius,
                    center = Offset(grain.x * size.width, grain.y * size.height),
                )
            }
        }
    }
}

@Composable
private fun AuroraBlob(
    color: Color,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to color.copy(alpha = alpha),
                    0.38f to color.copy(alpha = alpha * 0.68f),
                    0.72f to color.copy(alpha = alpha * 0.2f),
                    1.0f to Color.Transparent,
                )
            )
        )
    )
}
