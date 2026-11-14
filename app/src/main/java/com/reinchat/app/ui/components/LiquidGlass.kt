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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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

fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(28.dp),
    elevation: Dp = 0.dp,
    topTint: Color = ReinColors.GlassTop,
    bottomTint: Color = ReinColors.GlassBottom,
    borderAlpha: Float = 1f,
    glowColor: Color? = null,
): Modifier {
    var modifier: Modifier = this
    if (elevation > 0.dp) {
        modifier = if (glowColor != null) {
            modifier.shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = glowColor,
                spotColor = glowColor,
            )
        } else {
            modifier.shadow(elevation, shape, clip = false)
        }
    }
    return modifier
        .clip(shape)
        .background(Brush.verticalGradient(listOf(topTint, bottomTint)))
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.30f * borderAlpha),
                    ReinColors.Mint.copy(alpha = 0.14f * borderAlpha),
                    ReinColors.GlassBorderBottom.copy(alpha = ReinColors.GlassBorderBottom.alpha * borderAlpha),
                    ReinColors.Violet.copy(alpha = 0.10f * borderAlpha),
                )
            ),
            shape = shape,
        )
        .drawWithContent {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.055f * borderAlpha),
                    0.22f to Color.Transparent,
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = size.height * 0.5f,
                )
            )
            drawContent()
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.34f * borderAlpha),
                        Color.Transparent,
                    )
                ),
                start = Offset(size.width * 0.14f, 1f),
                end = Offset(size.width * 0.86f, 1f),
                strokeWidth = 1.dp.toPx(),
            )
        }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = onClick,
)

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.95f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 720f),
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
    shape: Shape = RoundedCornerShape(28.dp),
    elevation: Dp = 0.dp,
    topTint: Color = ReinColors.GlassTop,
    bottomTint: Color = ReinColors.GlassBottom,
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
    size: Dp = 42.dp,
    iconSize: Dp = 19.dp,
    tint: Color = ReinColors.TextPrimary,
    enabled: Boolean = true,
    background: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(size)
            .pressScale(interaction)
            .then(
                if (background) {
                    Modifier
                        .liquidGlass(
                            CircleShape,
                            topTint = Color.White.copy(alpha = 0.12f),
                            bottomTint = Color.White.copy(alpha = 0.045f),
                        )
                } else Modifier
            )
            .clip(CircleShape)
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
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .pressScale(interaction)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) {
                    Brush.linearGradient(listOf(ReinColors.Mint, Color(0xFF9DE7F9)))
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.05f))
                    )
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Color(0xFF052019) else ReinColors.TextTertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color(0xFF052019) else ReinColors.TextTertiary,
        )
    }
}

@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .liquidGlass(
                shape = RoundedCornerShape(50),
                topTint = Color.White.copy(alpha = 0.075f),
                bottomTint = Color.White.copy(alpha = 0.025f),
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
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
            .pressScale(interaction, 0.96f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@Composable
fun GlassSnackbar(data: SnackbarData) {
    GlassSurface(
        modifier = Modifier.padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = 18.dp,
        topTint = Color(0xF01A2320),
        bottomTint = Color(0xF00D1412),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = data.visuals.message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = ReinColors.TextPrimary,
            )
            data.visuals.actionLabel?.let { label ->
                Spacer(Modifier.size(12.dp))
                GlassTextButton(text = label, onClick = { data.performAction() })
            }
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
        cursorBrush = Brush.verticalGradient(listOf(ReinColors.Mint, ReinColors.Mint)),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = ReinColors.TextTertiary,
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
                .background(Color.Black.copy(alpha = 0.46f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .liquidGlass(
                        shape = RoundedCornerShape(30.dp),
                        elevation = 32.dp,
                        topTint = Color(0xF01C2522),
                        bottomTint = Color(0xF00C1311),
                    )
                    .padding(24.dp),
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
                .size(if (compact) 26.dp else 34.dp)
                .clip(RoundedCornerShape(if (compact) 9.dp else 12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            ReinColors.Mint,
                            ReinColors.Cyan,
                            ReinColors.Violet,
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.42f),
                        radius = size.minDimension * 0.26f,
                        center = Offset(size.width * 0.28f, size.height * 0.24f),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R",
                color = Color(0xFF062019),
                fontSize = if (compact) 15.sp else 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.size(if (compact) 8.dp else 10.dp))
        Text(
            text = "ReinChat",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = ReinColors.TextPrimary,
            letterSpacing = (-0.2).sp,
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
