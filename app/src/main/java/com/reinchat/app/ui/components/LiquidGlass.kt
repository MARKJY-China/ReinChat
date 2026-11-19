package com.reinchat.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reinchat.app.ui.theme.ReinColors

/**
 * Flat replacement for the old liquid-glass surface.
 *
 * The parameters are kept for call-site compatibility. [topTint] is now a plain
 * opaque fill (the callers pass paper, white or the soft accent), the border is a
 * single hairline, and the gradient/sheen/glow layers are gone.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(12.dp),
    elevation: Dp = 0.dp,
    topTint: Color = ReinColors.Surface,
    bottomTint: Color = Color.Unspecified,
    borderAlpha: Float = 1f,
    glowColor: Color? = null,
): Modifier {
    var modifier: Modifier = this
    if (elevation > 0.dp) {
        modifier = modifier.shadow(
            elevation = elevation.coerceAtMost(14.dp),
            shape = shape,
            clip = false,
        )
    }
    return modifier
        .clip(shape)
        .background(topTint, shape)
        .border(1.dp, ReinColors.Hairline, shape)
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = onClick,
)

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.98f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 900f),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin.Center
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    elevation: Dp = 0.dp,
    topTint: Color = ReinColors.Surface,
    bottomTint: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .liquidGlass(shape, elevation, topTint, bottomTint)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 18.dp,
    tint: Color = ReinColors.TextSecondary,
    enabled: Boolean = true,
    background: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) ReinColors.TextPrimary.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = if (enabled) tint.alpha else 0.32f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun LiquidPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val fg = if (enabled) ReinColors.OnInk else ReinColors.TextTertiary
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 46.dp)
            .pressScale(interaction)
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled) ReinColors.TextPrimary else ReinColors.TextPrimary.copy(alpha = 0.10f))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
        if (trailingIcon != null) {
            Spacer(Modifier.size(8.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(ReinColors.Surface)
            .border(1.dp, ReinColors.Hairline, RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ReinColors.Mint,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@Composable
fun GlassSnackbar(data: SnackbarData) {
    Row(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(ReinColors.Surface)
            .border(1.dp, ReinColors.Hairline, RoundedCornerShape(11.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = data.visuals.message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = ReinColors.TextPrimary,
        )
        data.visuals.actionLabel?.let { label ->
            Spacer(Modifier.size(10.dp))
            GlassTextButton(text = label, onClick = { data.performAction() })
        }
    }
}

@Composable
fun ReinSnackbarHost(hostState: androidx.compose.material3.SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        GlassSnackbar(data)
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = LocalTextStyle.current.copy(
        color = ReinColors.TextPrimary,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(ReinColors.Mint),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = ReinColors.Placeholder,
                            maxLines = if (singleLine) 1 else maxLines,
                        )
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReinColors.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .padding(horizontal = 22.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .clip(RoundedCornerShape(17.dp))
                    .background(ReinColors.Surface)
                    .border(1.dp, ReinColors.Hairline, RoundedCornerShape(17.dp))
                    .padding(22.dp),
                content = content,
            )
        }
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(if (compact) 22.dp else 26.dp)
                .clip(RoundedCornerShape(if (compact) 7.dp else 8.dp))
                .background(ReinColors.TextPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R",
                color = ReinColors.OnInk,
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.size(if (compact) 8.dp else 9.dp))
        Text(
            text = "ReinChat",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = if (compact) 14.sp else 16.sp),
            fontWeight = FontWeight.Bold,
            color = ReinColors.TextPrimary,
            letterSpacing = (-0.3).sp,
        )
    }
}

@Composable
fun PainterIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
