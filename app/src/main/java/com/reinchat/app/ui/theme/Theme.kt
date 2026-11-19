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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Palette is rewritten by ReinTheme before the tree composes. The names are kept
 * from the previous liquid-glass theme so existing call sites keep compiling, but
 * every value now resolves to the flat paper/ink palette of the web client.
 */
object ReinColors {
    /** Screen canvas. */
    var Deep by mutableStateOf(Color(0xFFFBFAF7))

    /** Paper rail / subtle raised plane. */
    var DeepElevated by mutableStateOf(Color(0xFFF4F2ED))

    /** Ink plate used for the wordmark and dark UI accents. */
    var Ink by mutableStateOf(Color(0xFFEFEDE6))

    var TextPrimary by mutableStateOf(Color(0xFF161613))
    var TextSecondary by mutableStateOf(Color(0xFF7B7871))
    var TextTertiary by mutableStateOf(Color(0xFFA5A096))

    /** Restrained accent: status dots, links, selected states. */
    var Mint by mutableStateOf(Color(0xFF187A55))
    var MintDeep by mutableStateOf(Color(0xFF187A55))
    var Cyan by mutableStateOf(Color(0xFF187A55))
    var Violet by mutableStateOf(Color(0xFF8B857A))
    var Warm by mutableStateOf(Color(0xFFB07C3A))

    /** Flat card / composer / dialog fill. */
    var Surface by mutableStateOf(Color(0xFFFFFEFA))

    /** 1px hairline used for every border and separator. */
    var Hairline by mutableStateOf(Color(0xFFE7E3DA))
    var HairlineStrong by mutableStateOf(Color(0xFFC6C0B3))

    /** Tinted accent plane for selected chips and quiet states. */
    var AccentSoft by mutableStateOf(Color(0xFFE5F2EB))

    /** Text colour that sits on [TextPrimary] (inverted primary button). */
    var OnInk by mutableStateOf(Color(0xFFFBFAF7))

    /** Knob / glyph colour that sits on the accent green. */
    var OnAccent by mutableStateOf(Color(0xFFFFFEFA))

    /** Placeholder / disabled field text. */
    var Placeholder by mutableStateOf(Color(0xFFAAA59A))

    /** Dim layer behind sheets and dialogs. */
    var Scrim by mutableStateOf(Color(0x2E1E1D18))

    var GlassTop by mutableStateOf(Color(0xFFFFFEFA))
    var GlassBottom by mutableStateOf(Color(0xFFFFFEFA))
    var GlassBorderTop by mutableStateOf(Color(0xFFE7E3DA))
    var GlassBorderBottom by mutableStateOf(Color(0xFFE7E3DA))
    var UserBubbleTop by mutableStateOf(Color(0xFFEFEDE6))
    var UserBubbleBottom by mutableStateOf(Color(0xFFEFEDE6))
    var Danger by mutableStateOf(Color(0xFFB44336))

    internal fun apply(dark: Boolean) {
        if (dark) {
            Deep = Color(0xFF11120F); DeepElevated = Color(0xFF181915); Ink = Color(0xFF1D1E19)
            TextPrimary = Color(0xFFE9E6DE); TextSecondary = Color(0xFF9A958A); TextTertiary = Color(0xFF777267)
            Mint = Color(0xFF82D7B0); MintDeep = Color(0xFF82D7B0); Cyan = Color(0xFF82D7B0)
            Violet = Color(0xFFB9B3A5); Warm = Color(0xFFD7B47D)
            Surface = Color(0xFF1D1E19)
            Hairline = Color(0xFF2B2C26); HairlineStrong = Color(0xFF4B4C42)
            AccentSoft = Color(0xFF1D3A2C)
            OnInk = Color(0xFF11120F)
            OnAccent = Color(0xFF0B241A)
            Placeholder = Color(0xFF777267)
            Scrim = Color(0x73000000)
            UserBubbleTop = Color(0xFF23241F); UserBubbleBottom = Color(0xFF23241F)
            Danger = Color(0xFFF09588)
            GlassTop = Color(0xFF1D1E19); GlassBottom = Color(0xFF1D1E19)
            GlassBorderTop = Color(0xFF2B2C26); GlassBorderBottom = Color(0xFF2B2C26)
        } else {
            Deep = Color(0xFFFBFAF7); DeepElevated = Color(0xFFF4F2ED); Ink = Color(0xFFEFEDE6)
            TextPrimary = Color(0xFF161613); TextSecondary = Color(0xFF7B7871); TextTertiary = Color(0xFFA5A096)
            Mint = Color(0xFF187A55); MintDeep = Color(0xFF187A55); Cyan = Color(0xFF187A55)
            Violet = Color(0xFF8B857A); Warm = Color(0xFFB07C3A)
            Surface = Color(0xFFFFFEFA)
            Hairline = Color(0xFFE7E3DA); HairlineStrong = Color(0xFFC6C0B3)
            AccentSoft = Color(0xFFE5F2EB)
            OnInk = Color(0xFFFBFAF7)
            OnAccent = Color(0xFFFFFEFA)
            Placeholder = Color(0xFFAAA59A)
            Scrim = Color(0x3D1E1D18)
            UserBubbleTop = Color(0xFFEFEDE6); UserBubbleBottom = Color(0xFFEFEDE6)
            Danger = Color(0xFFB44336)
            GlassTop = Color(0xFFFFFEFA); GlassBottom = Color(0xFFFFFEFA)
            GlassBorderTop = Color(0xFFE7E3DA); GlassBorderBottom = Color(0xFFE7E3DA)
        }
    }
}

/** Exposed to screens so they can offer a theme toggle without any data-layer state. */
data class ReinThemeControl(
    val darkTheme: Boolean,
    val onToggle: () -> Unit,
)

val LocalReinThemeControl = staticCompositionLocalOf<ReinThemeControl?> { null }

val LiquidEase = CubicBezierEasing(0.22f, 0.8f, 0.2f, 1f)
val LiquidEaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
val LiquidEaseOutSoft = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
val GentleSpring = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

private val ReinTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.6).sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.9).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 31.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, lineHeight = 15.sp, letterSpacing = 0.2.sp),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF187A55), onPrimary = Color(0xFFFFFEFA),
    primaryContainer = Color(0xFFE5F2EB), onPrimaryContainer = Color(0xFF0E4A34),
    secondary = Color(0xFF555148), onSecondary = Color(0xFFFFFEFA),
    background = Color(0xFFFBFAF7), onBackground = Color(0xFF161613),
    surface = Color(0xFFFFFEFA), onSurface = Color(0xFF161613),
    surfaceVariant = Color(0xFFF4F2ED), onSurfaceVariant = Color(0xFF7B7871),
    outline = Color(0xFFE7E3DA), error = Color(0xFFB44336), onError = Color(0xFFFFFEFA),
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
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
