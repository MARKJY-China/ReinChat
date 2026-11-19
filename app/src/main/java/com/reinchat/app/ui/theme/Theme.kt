package com.reinchat.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette is rewritten by ReinTheme before the tree composes. */
object ReinColors {
    var Deep = Color(0xFFFBFAF7)
    var DeepElevated = Color(0xFFF4F2ED)
    var Ink = Color(0xFFEFEDE6)
    var TextPrimary = Color(0xFF161613)
    var TextSecondary = Color(0xFF7B7871)
    var TextTertiary = Color(0xFFA5A096)
    var Mint = Color(0xFF187A55)
    var MintDeep = Color(0xFF187A55)
    var Cyan = Color(0xFF187A55)
    var Violet = Color(0xFF8B857A)
    var Warm = Color(0xFFB07C3A)
    var GlassTop = Color(0xFFFFFFFF)
    var GlassBottom = Color(0xFFF7F5F0)
    var GlassBorderTop = Color(0xFFE7E3DA)
    var GlassBorderBottom = Color(0xFFE7E3DA)
    var UserBubbleTop = Color(0xFFEFEDE6)
    var UserBubbleBottom = Color(0xFFEFEDE6)
    var Danger = Color(0xFFB44336)

    internal fun apply(dark: Boolean) {
        if (dark) {
            Deep = Color(0xFF11120F); DeepElevated = Color(0xFF181915); Ink = Color(0xFF1D1E19)
            TextPrimary = Color(0xFFE9E6DE); TextSecondary = Color(0xFF9A958A); TextTertiary = Color(0xFF777267)
            Mint = Color(0xFF82D7B0); MintDeep = Color(0xFF82D7B0); Cyan = Color(0xFF82D7B0)
            Violet = Color(0xFFB9B3A5); Warm = Color(0xFFD7B47D)
            GlassTop = Color(0xFF1D1E19); GlassBottom = Color(0xFF191A16)
            GlassBorderTop = Color(0xFF2B2C26); GlassBorderBottom = Color(0xFF2B2C26)
            UserBubbleTop = Color(0xFF23241F); UserBubbleBottom = Color(0xFF23241F)
            Danger = Color(0xFFF09588)
        } else {
            Deep = Color(0xFFFBFAF7); DeepElevated = Color(0xFFF4F2ED); Ink = Color(0xFFEFEDE6)
            TextPrimary = Color(0xFF161613); TextSecondary = Color(0xFF7B7871); TextTertiary = Color(0xFFA5A096)
            Mint = Color(0xFF187A55); MintDeep = Color(0xFF187A55); Cyan = Color(0xFF187A55)
            Violet = Color(0xFF8B857A); Warm = Color(0xFFB07C3A)
            GlassTop = Color(0xFFFFFFFF); GlassBottom = Color(0xFFF7F5F0)
            GlassBorderTop = Color(0xFFE7E3DA); GlassBorderBottom = Color(0xFFE7E3DA)
            UserBubbleTop = Color(0xFFEFEDE6); UserBubbleBottom = Color(0xFFEFEDE6)
            Danger = Color(0xFFB44336)
        }
    }
}

val LiquidEase = CubicBezierEasing(0.22f, 0.8f, 0.2f, 1f)
val LiquidEaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
val LiquidEaseOutSoft = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
val GentleSpring = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

private val ReinTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.4).sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 25.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF187A55), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F2EB), onPrimaryContainer = Color(0xFF0E4A34),
    secondary = Color(0xFF555148), onSecondary = Color.White,
    background = Color(0xFFFBFAF7), onBackground = Color(0xFF161613),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF161613),
    surfaceVariant = Color(0xFFF4F2ED), onSurfaceVariant = Color(0xFF7B7871),
    outline = Color(0xFFE7E3DA), error = Color(0xFFB44336), onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF82D7B0), onPrimary = Color(0xFF0B241A),
    primaryContainer = Color(0xFF1D3A2C), onPrimaryContainer = Color(0xFFA9E8CC),
    secondary = Color(0xFFC8C4BA), onSecondary = Color(0xFF1A1B17),
    background = Color(0xFF11120F), onBackground = Color(0xFFE9E6DE),
    surface = Color(0xFF1D1E19), onSurface = Color(0xFFE9E6DE),
    surfaceVariant = Color(0xFF181915), onSurfaceVariant = Color(0xFF9A958A),
    outline = Color(0xFF2B2C26), error = Color(0xFFF09588), onError = Color(0xFF300805),
)

@Composable
fun ReinTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    ReinColors.apply(darkTheme)
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ReinTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
