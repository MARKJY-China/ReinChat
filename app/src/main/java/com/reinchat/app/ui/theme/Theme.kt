package com.reinchat.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ReinColors {
    val Deep = Color(0xFF050807)
    val DeepElevated = Color(0xFF0A100F)
    val Ink = Color(0xFF151C1A)

    val TextPrimary = Color(0xFFF4F8F6)
    val TextSecondary = Color(0xFFA6B3AE)
    val TextTertiary = Color(0xFF6E7B76)

    val Mint = Color(0xFF8BF3CE)
    val MintDeep = Color(0xFF40D6A4)
    val Cyan = Color(0xFF84D8F5)
    val Violet = Color(0xFFBCA7FF)
    val Warm = Color(0xFFF4D9AD)

    val GlassTop = Color(0x1EFFFFFF)
    val GlassBottom = Color(0x0AFFFFFF)
    val GlassBorderTop = Color(0x4DFFFFFF)
    val GlassBorderBottom = Color(0x12FFFFFF)
    val UserBubbleTop = Color(0x3A9FF4D4)
    val UserBubbleBottom = Color(0x226EC8E8)

    val Danger = Color(0xFFFFA8A2)
}

val LiquidEase = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)
val LiquidEaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
val LiquidEaseOutSoft = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
val GentleSpring = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

private val ReinTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val ReinDarkScheme = darkColorScheme(
    primary = ReinColors.Mint,
    onPrimary = Color(0xFF042019),
    primaryContainer = Color(0xFF173D33),
    onPrimaryContainer = ReinColors.Mint,
    secondary = ReinColors.Cyan,
    onSecondary = Color(0xFF04202A),
    tertiary = ReinColors.Violet,
    background = ReinColors.Deep,
    onBackground = ReinColors.TextPrimary,
    surface = ReinColors.DeepElevated,
    onSurface = ReinColors.TextPrimary,
    surfaceVariant = Color(0xFF16201D),
    onSurfaceVariant = ReinColors.TextSecondary,
    outline = Color(0xFF31403A),
    error = ReinColors.Danger,
    onError = Color(0xFF300805),
)

@Composable
fun ReinTheme(content: @Composable () -> Unit) {
    // The visual language is intentionally dark even when the system is light:
    // translucent layers need a deep, stable canvas to feel like liquid glass.
    MaterialTheme(
        colorScheme = ReinDarkScheme,
        typography = ReinTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
        ),
        content = content,
    )
}
