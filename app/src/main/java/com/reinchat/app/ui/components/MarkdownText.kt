package com.reinchat.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reinchat.app.ui.theme.LiquidEaseInOut
import com.reinchat.app.ui.theme.ReinColors
import kotlinx.coroutines.delay

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    color: Color = ReinColors.TextPrimary,
) {
    val blocks = remember(markdown) { MarkdownParser.parse(MarkdownParser.sanitizeDSML(markdown)) }
    val transition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(580, easing = LiquidEaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretAlpha",
    )

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            blocks.forEachIndexed { index, block ->
                val isLast = index == blocks.lastIndex
                RenderBlock(
                    block = block,
                    color = color,
                    showCaret = streaming && isLast,
                    caretAlpha = caretAlpha,
                )
            }
            if (streaming && blocks.isEmpty()) {
                Text(
                    text = "▍",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ReinColors.Mint.copy(alpha = caretAlpha),
                )
            }
        }
    }
}

@Composable
private fun RenderBlock(
    block: MdBlock,
    color: Color,
    showCaret: Boolean,
    caretAlpha: Float,
) {
    when (block) {
        is MdBlock.Paragraph -> {
            Text(
                text = annotate(block.inlines, color, showCaret, caretAlpha),
                style = MaterialTheme.typography.bodyLarge,
                color = color,
            )
        }

        is MdBlock.Heading -> {
            val (size, weight, paddingTop) = when (block.level) {
                1 -> Triple(24.sp, FontWeight.SemiBold, 8.dp)
                2 -> Triple(21.sp, FontWeight.SemiBold, 6.dp)
                3 -> Triple(18.sp, FontWeight.SemiBold, 4.dp)
                else -> Triple(16.sp, FontWeight.SemiBold, 2.dp)
            }
            Text(
                text = annotate(block.inlines, color, showCaret, caretAlpha),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = size,
                    lineHeight = size * 1.38f,
                    fontWeight = weight,
                ),
                color = color,
                modifier = Modifier.padding(top = paddingTop),
            )
        }

        is MdBlock.BulletList -> {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                block.items.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .size(4.5.dp)
                                .clip(CircleShape)
                                .background(ReinColors.Mint.copy(alpha = 0.85f))
                        )
                        Spacer(Modifier.width(11.dp))
                        Text(
                            text = annotate(
                                item,
                                color,
                                showCaret && index == block.items.lastIndex,
                                caretAlpha,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        is MdBlock.OrderedList -> {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                block.items.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${block.start + index}.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFeatureSettings = "tnum",
                            ),
                            color = ReinColors.Mint.copy(alpha = 0.88f),
                            modifier = Modifier.width(24.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = annotate(
                                item,
                                color,
                                showCaret && index == block.items.lastIndex,
                                caretAlpha,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        is MdBlock.Table -> {
            MarkdownTable(block = block, color = color)
        }

        is MdBlock.Quote -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRoundRect(
                            color = ReinColors.Mint.copy(alpha = 0.38f),
                            topLeft = Offset(0f, 3.dp.toPx()),
                            size = Size(2.dp.toPx(), size.height - 6.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                    .padding(start = 14.dp, top = 3.dp, bottom = 3.dp),
            ) {
                Text(
                    text = annotate(block.inlines, ReinColors.TextSecondary, showCaret, caretAlpha),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontStyle = FontStyle.Italic,
                    ),
                    color = ReinColors.TextSecondary,
                )
            }
        }

        is MdBlock.CodeBlock -> CodeBlockCard(
            block = block,
            streaming = showCaret,
            caretAlpha = caretAlpha,
        )

        MdBlock.Divider -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent,
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun MarkdownTable(block: MdBlock.Table, color: Color) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.028f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReinColors.Mint.copy(alpha = 0.055f)),
            verticalAlignment = Alignment.Top,
        ) {
            block.header.forEachIndexed { index, cell ->
                Text(
                    text = annotate(cell, color, false, 1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    textAlign = block.alignments.getOrNull(index).toTextAlign(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                )
            }
        }
        block.rows.forEach { row ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                row.forEachIndexed { index, cell ->
                    Text(
                        text = annotate(cell, color, false, 1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        textAlign = block.alignments.getOrNull(index).toTextAlign(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private fun MdAlign?.toTextAlign(): TextAlign = when (this) {
    MdAlign.CENTER -> TextAlign.Center
    MdAlign.RIGHT -> TextAlign.End
    else -> TextAlign.Start
}

@Composable
private fun CodeBlockCard(block: MdBlock.CodeBlock, streaming: Boolean, caretAlpha: Float) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val canScrollHorizontally = remember(block.code) {
        block.code.lineSequence().any { it.length > 46 }
    }
    val shape = RoundedCornerShape(16.dp)
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xCC080E0C))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.028f))
                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (streaming) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(ReinColors.Mint.copy(alpha = caretAlpha))
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = block.language.ifBlank { "code" }.lowercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.4.sp,
                ),
                color = ReinColors.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            if (canScrollHorizontally) {
                Text(
                    text = "↔",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    color = ReinColors.TextTertiary.copy(alpha = 0.55f),
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            CopyIconButton(
                copied = copied,
                onClick = {
                    clipboard.setText(AnnotatedString(block.code))
                    copied = true
                },
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = block.code + if (streaming) "▍" else "",
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFFD6E5DF),
                ),
                softWrap = false,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color(0xE6080E0C)),
                                startX = size.width - 34.dp.toPx(),
                                endX = size.width,
                            )
                        )
                    }
            )
        }
    }
}

@Composable
private fun CopyIconButton(copied: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .clickableNoRipple(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "copied",
        ) {
            Icon(
                imageVector = if (it) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = if (it) "已复制" else "复制代码",
                tint = if (it) ReinColors.Mint else ReinColors.TextTertiary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

private fun annotate(
    inlines: List<MdInline>,
    baseColor: Color,
    showCaret: Boolean,
    caretAlpha: Float,
): AnnotatedString = buildAnnotatedString {
    appendInlines(inlines, SpanStyle(color = baseColor))
    if (showCaret) {
        withStyle(
            SpanStyle(
                color = ReinColors.Mint.copy(alpha = caretAlpha),
                fontWeight = FontWeight.Light,
            )
        ) {
            append("▍")
        }
    }
}

private fun AnnotatedString.Builder.appendInlines(
    inlines: List<MdInline>,
    style: SpanStyle,
) {
    inlines.forEach { inline ->
        when (inline) {
            is MdInline.Plain -> withStyle(style) { append(inline.text) }
            is MdInline.Strong -> appendInlines(
                inline.children,
                style.merge(SpanStyle(fontWeight = FontWeight.SemiBold)),
            )
            is MdInline.Emphasis -> appendInlines(
                inline.children,
                style.merge(SpanStyle(fontStyle = FontStyle.Italic)),
            )
            is MdInline.Strike -> appendInlines(
                inline.children,
                style.merge(SpanStyle(textDecoration = TextDecoration.LineThrough)),
            )
            is MdInline.Code -> withStyle(
                style.merge(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = ReinColors.Mint,
                        background = Color(0x2A7BE6C2),
                    )
                )
            ) {
                append(inline.text)
            }
            is MdInline.Link -> {
                val linkStyle = style.merge(
                    SpanStyle(
                        color = ReinColors.Cyan,
                        textDecoration = TextDecoration.Underline,
                    )
                )
                withLink(
                    LinkAnnotation.Url(
                        url = inline.url,
                        styles = TextLinkStyles(style = linkStyle),
                    )
                ) {
                    appendInlines(inline.label, linkStyle)
                }
            }
        }
    }
}
