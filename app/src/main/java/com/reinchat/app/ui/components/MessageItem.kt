package com.reinchat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.reinchat.app.data.ChatImage
import com.reinchat.app.data.ChatMessage
import com.reinchat.app.data.MessageRole
import com.reinchat.app.data.ProcessTimeline
import com.reinchat.app.data.MessageSegment
import com.reinchat.app.data.MessageStatus
import com.reinchat.app.data.PROCESS_LABEL_THINKING_ACTIVE
import com.reinchat.app.data.PROCESS_LABEL_THINKING_DONE
import com.reinchat.app.data.normalizeToolStatus
import com.reinchat.app.data.thinkingSegmentLabel
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.LiquidEaseInOut
import com.reinchat.app.ui.theme.LiquidEaseOutSoft
import com.reinchat.app.ui.theme.ReinColors
import com.reinchat.app.vm.StreamPhase

/**
 * A single conversational turn, styled after the web client: the user writes in a
 * subtle right-aligned paper bubble, the assistant answers as plain prose, and the
 * reasoning stream folds away behind a quiet chevron.
 */
@Composable
fun MessageItem(
    message: ChatMessage,
    animateIn: Boolean,
    isStreaming: Boolean,
    streamPhase: StreamPhase,
    canRewind: Boolean,
    actionsOpen: Boolean,
    onToggleActions: () -> Unit,
    onCloseActions: () -> Unit,
    onCopy: (ChatMessage) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onRewind: (ChatMessage) -> Unit,
    onOpenImage: (ChatImage) -> Unit,
    showDivider: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val entrance = remember(message.id) { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(message.id) {
        if (animateIn) {
            entrance.animateTo(1f, animationSpec = tween(320, easing = LiquidEase))
        }
    }
    val contentAlpha = entrance.value
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = (1f - contentAlpha) * 8.dp.toPx()
            }
            .padding(top = 18.dp, bottom = if (showDivider) 0.dp else 22.dp)
    ) {
        if (message.role == MessageRole.USER) {
            UserMessage(
                message = message,
                isStreaming = isStreaming,
                canRewind = canRewind,
                actionsOpen = actionsOpen,
                onToggleActions = onToggleActions,
                onCloseActions = onCloseActions,
                onCopy = onCopy,
                onEdit = onEdit,
                onRewind = onRewind,
                onOpenImage = onOpenImage,
            )
        } else {
            AssistantMessage(
                message = message,
                isStreaming = isStreaming,
                streamPhase = streamPhase,
                canRewind = canRewind,
                actionsOpen = actionsOpen,
                onToggleActions = onToggleActions,
                onCloseActions = onCloseActions,
                onCopy = onCopy,
                onRetry = onRetry,
                onRewind = onRewind,
            )
        }
        if (showDivider) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ReinColors.Hairline)
            )
        }
    }
}

@Composable
private fun UserMessage(
    message: ChatMessage,
    isStreaming: Boolean,
    canRewind: Boolean,
    actionsOpen: Boolean,
    onToggleActions: () -> Unit,
    onCloseActions: () -> Unit,
    onCopy: (ChatMessage) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onRewind: (ChatMessage) -> Unit,
    onOpenImage: (ChatImage) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubble = minOf(maxWidth * 0.86f, 520.dp)
        val hasMedia = message.images.isNotEmpty() || message.hasPdf
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            Column(
                modifier = Modifier
                    .then(
                        if (message.images.isNotEmpty()) Modifier.width(maxBubble)
                        else Modifier.widthIn(max = maxBubble)
                    )
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomEnd = 4.dp,
                            bottomStart = 14.dp,
                        )
                    )
                    .background(ReinColors.UserBubbleTop)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (!isStreaming) onToggleActions()
                    }
                    .padding(if (hasMedia) 5.dp else 0.dp),
            ) {
                if (message.hasPdf) {
                    PdfMessageChip(
                        name = message.pdfName.ifBlank { "文档.pdf" },
                        modifier = Modifier.padding(
                            start = if (message.images.isNotEmpty()) 5.dp else 7.dp,
                            end = 5.dp,
                            top = 2.dp,
                            bottom = if (message.images.isNotEmpty()) 3.dp else 0.dp,
                        ),
                    )
                }
                if (message.images.isNotEmpty()) {
                    UserImageGrid(
                        images = message.images,
                        onOpenImage = onOpenImage,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(
                            horizontal = if (hasMedia) 12.dp else 15.dp,
                            vertical = if (hasMedia) 10.dp else 11.dp,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ReinColors.TextPrimary,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatClock(message.createdAt),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                    color = ReinColors.TextTertiary,
                )
                if (message.content.isNotBlank() && !isStreaming) {
                    Spacer(Modifier.width(4.dp))
                    QuietAction(
                        text = "复制",
                        onClick = { onCopy(message) },
                    )
                }
                MessageMenuButton(
                    expanded = actionsOpen,
                    enabled = !isStreaming,
                    onClick = onToggleActions,
                )
            }

            AnimatedVisibility(
                visible = actionsOpen,
                enter = fadeIn(tween(140)) + expandVertically(
                    animationSpec = tween(200, easing = LiquidEaseOutSoft),
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(tween(100)) + shrinkVertically(
                    animationSpec = tween(150, easing = LiquidEaseOutSoft),
                    shrinkTowards = Alignment.Top,
                ),
            ) {
                MessageActionRow(
                    modifier = Modifier.padding(top = 4.dp),
                    actions = buildList {
                        if (message.content.isNotBlank()) {
                            add(
                                MessageAction("复制", Icons.Rounded.ContentCopy) {
                                    onCloseActions()
                                    onCopy(message)
                                }
                            )
                        }
                        add(
                            MessageAction("编辑", Icons.Rounded.Edit) {
                                onCloseActions()
                                onEdit(message)
                            }
                        )
                        add(
                            MessageAction(
                                label = "回退",
                                icon = Icons.Rounded.Replay,
                                enabled = canRewind,
                                onClick = {
                                    onCloseActions()
                                    onRewind(message)
                                },
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    isStreaming: Boolean,
    streamPhase: StreamPhase,
    canRewind: Boolean,
    actionsOpen: Boolean,
    onToggleActions: () -> Unit,
    onCloseActions: () -> Unit,
    onCopy: (ChatMessage) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onRewind: (ChatMessage) -> Unit,
) {
    val hasContent = message.content.isNotBlank()
    val segments = message.segments
    val (workSegments, trailingText) = ProcessTimeline.splitAnswer(segments)
    val hasWorkSteps = segments.any { it.type != MessageSegment.TYPE_TEXT }
    val hasInlineText = segments.any { it.type == MessageSegment.TYPE_TEXT }
    // When work history is folded away the answer must come only from the trailing
    // text, otherwise interim reports would be duplicated at the bottom.
    val showWorkBlock = !isStreaming && workSegments.isNotEmpty() && hasWorkSteps
    val finalText = ProcessTimeline.answerText(segments, message.content)
    val showPlainContent = hasContent && !(showWorkBlock && trailingText.isNotBlank())

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.model.ifBlank { "ReinChat" }.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = ReinColors.Mint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = formatClock(message.createdAt),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                color = ReinColors.TextTertiary,
            )
            if (message.status.isCancelled && hasContent) {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "已停止",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                    color = ReinColors.TextTertiary,
                )
            }
            Spacer(Modifier.weight(1f))
            if (hasContent && !isStreaming) {
                QuietAction(text = "复制", onClick = { onCopy(message) })
            }
            MessageMenuButton(
                expanded = actionsOpen,
                enabled = !isStreaming,
                onClick = onToggleActions,
            )
        }

        if (showWorkBlock) {
            CompletedWorkBlock(messageId = message.id, segments = workSegments)
            if (finalText.isNotBlank()) Spacer(Modifier.height(10.dp))
        } else if (segments.isNotEmpty()) {
            SegmentTimeline(
                messageId = message.id,
                segments = segments,
                isStreaming = isStreaming,
                hasContent = hasContent,
            )
            if ((hasContent && !hasInlineText) || isStreaming) Spacer(Modifier.height(10.dp))
        } else if (message.reasoning.isNotBlank()) {
            // Old conversations only stored the concatenated reasoning text.
            ThinkingBlock(
                message = message,
                hasContent = hasContent,
                isStreaming = isStreaming,
            )
            if (hasContent) Spacer(Modifier.height(10.dp))
        }

        if (showWorkBlock) {
            if (finalText.isNotBlank()) {
                MarkdownText(
                    markdown = finalText,
                    modifier = Modifier.fillMaxWidth(),
                    streaming = false,
                    color = ReinColors.TextPrimary.copy(alpha = 0.94f),
                )
            }
            if (message.status == MessageStatus.ERROR) {
                ErrorNotice(
                    message = message.error ?: "生成中断",
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        } else {
            when {
                hasContent && !hasInlineText -> {
                    MarkdownText(
                        markdown = message.content,
                        modifier = Modifier.fillMaxWidth(),
                        streaming = isStreaming,
                        color = ReinColors.TextPrimary.copy(alpha = 0.94f),
                    )
                    if (message.status == MessageStatus.ERROR) {
                        ErrorNotice(
                            message = message.error ?: "生成中断",
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }

                hasInlineText -> Unit

                // The phase line below already tells the user what is happening.
                isStreaming -> Unit

                message.status == MessageStatus.ERROR -> {
                    ErrorNotice(message = message.error ?: "生成失败，请稍后重试")
                }

                else -> {
                    Text(
                        text = "未生成正文",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReinColors.TextTertiary,
                    )
                }
            }
        }

        if (isStreaming && (hasContent || segments.isEmpty())) {
            StreamPhaseLine(phase = streamPhase, hasContent = hasContent)
        }

        AnimatedVisibility(
            visible = actionsOpen,
            enter = fadeIn(tween(140)) + expandVertically(
                animationSpec = tween(200, easing = LiquidEaseOutSoft),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(tween(100)) + shrinkVertically(
                animationSpec = tween(150, easing = LiquidEaseOutSoft),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            MessageActionRow(
                modifier = Modifier.padding(top = 6.dp),
                actions = buildList {
                    if (message.status == MessageStatus.ERROR) {
                        add(
                            MessageAction("重试", Icons.Rounded.Refresh) {
                                onCloseActions()
                                onRetry(message)
                            }
                        )
                    }
                    if (hasContent) {
                        add(
                            MessageAction("复制", Icons.Rounded.ContentCopy) {
                                onCloseActions()
                                onCopy(message)
                            }
                        )
                    }
                    add(
                        MessageAction(
                            label = "回退",
                            icon = Icons.Rounded.Replay,
                            enabled = canRewind,
                            onClick = {
                                onCloseActions()
                                onRewind(message)
                            },
                        )
                    )
                },
            )
        }
    }
}

/**
 * "正在思考" while waiting for the first token (or while only thinking has
 * arrived), "AI 正在输出" once the answer body starts streaming.
 */
@Composable
private fun StreamPhaseLine(phase: StreamPhase, hasContent: Boolean) {
    if (phase == StreamPhase.IDLE) return
    val stillThinking = !hasContent
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stillThinking) {
            ThinkingDots()
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = if (hasContent) "AI 正在输出" else PROCESS_LABEL_THINKING_ACTIVE,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp),
            color = ReinColors.TextTertiary,
        )
    }
}

/** Body text the model wrote before it continued working. */
@Composable
private fun InterimText(segment: MessageSegment) {
    MarkdownText(
        markdown = segment.text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp),
        streaming = false,
        color = ReinColors.TextPrimary.copy(alpha = 0.88f),
    )
}

/**
 * Folded work history of a finished turn: reasoning blocks, tool cards and the
 * interim text the model wrote in between. Collapsed by default so the final
 * answer stays the focus.
 */
@Composable
private fun CompletedWorkBlock(
    messageId: String,
    segments: List<MessageSegment>,
) {
    var expanded by remember(messageId) { mutableStateOf(false) }
    val toolCount = segments.count { it.type == MessageSegment.TYPE_TOOL }
    val reasoningChars = segments
        .filter { it.type == MessageSegment.TYPE_REASONING }
        .sumOf { it.text.length }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(180, easing = LiquidEase),
        label = "workChevron",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = !expanded }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = ReinColors.TextTertiary,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = chevron },
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "已完成工作",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                color = ReinColors.TextSecondary,
            )
            if (toolCount > 0) {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "$toolCount 项工具",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    color = ReinColors.TextTertiary,
                )
            }
            if (reasoningChars > 0) {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "$reasoningChars 字",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    color = ReinColors.TextTertiary,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(140)) + expandVertically(
                animationSpec = tween(200, easing = LiquidEaseOutSoft),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(tween(100)) + shrinkVertically(
                animationSpec = tween(160, easing = LiquidEaseOutSoft),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 4.dp, bottom = 2.dp),
            ) {
                SegmentTimeline(
                    messageId = messageId,
                    segments = segments,
                    isStreaming = false,
                    hasContent = true,
                )
            }
        }
    }
}

/**
 * Chronological reasoning / tool timeline rebuilt from [ChatMessage.segments].
 *
 * A tool card always ends the thinking block before it, and the next reasoning
 * delta starts a new block. While the message streams, only the trailing
 * thinking block (no answer text yet, no tool after it) stays expanded; every
 * completed block collapses automatically unless the user toggled it.
 */
@Composable
private fun SegmentTimeline(
    messageId: String,
    segments: List<MessageSegment>,
    isStreaming: Boolean,
    hasContent: Boolean,
) {
    val firstReasoningIndex = segments.indexOfFirst { it.type == MessageSegment.TYPE_REASONING }
    val lastIndex = segments.lastIndex
    val activeReasoningIndex = if (isStreaming && !hasContent && lastIndex >= 0 &&
        segments[lastIndex].type == MessageSegment.TYPE_REASONING
    ) {
        lastIndex
    } else {
        -1
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            when (segment.type) {
                MessageSegment.TYPE_REASONING -> ReasoningSegment(
                    messageId = messageId,
                    index = index,
                    segment = segment,
                    active = index == activeReasoningIndex,
                    first = index == firstReasoningIndex,
                )

                MessageSegment.TYPE_TOOL -> ToolSegment(
                    messageId = messageId,
                    index = index,
                    segment = segment,
                )

                MessageSegment.TYPE_TEXT -> InterimText(segment = segment)

                else -> Unit
            }
        }
    }
}

@Composable
private fun ReasoningSegment(
    messageId: String,
    index: Int,
    segment: MessageSegment,
    active: Boolean,
    first: Boolean,
) {
    var userToggled by remember(messageId, index) { mutableStateOf(false) }
    var expanded by remember(messageId, index) { mutableStateOf(active) }
    val scrollState = rememberScrollState()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200, easing = LiquidEaseOutSoft),
        label = "reasoningChevron",
    )

    // Auto-expand the live thinking block and auto-collapse it once it ends.
    LaunchedEffect(active) {
        if (!userToggled) expanded = active
    }
    LaunchedEffect(segment.text, expanded) {
        if (expanded && active) scrollState.scrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    userToggled = true
                    expanded = !expanded
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentChevron(rotation = chevronRotation)
            Spacer(Modifier.width(6.dp))
            Text(
                text = thinkingSegmentLabel(active = active, first = first),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                color = if (active) ReinColors.TextPrimary else ReinColors.TextSecondary,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "${segment.text.length} 字",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                color = ReinColors.TextTertiary.copy(alpha = 0.8f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (active) "思考中…" else "已思考",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                color = if (active) ReinColors.Mint.copy(alpha = 0.8f) else ReinColors.TextTertiary,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(200, easing = LiquidEaseOutSoft),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(140)),
            exit = shrinkVertically(
                animationSpec = tween(150, easing = LiquidEaseOutSoft),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(90)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 5.dp, top = 3.dp, bottom = 4.dp)
                    .heightIn(max = 280.dp)
                    .drawBehind {
                        drawLine(
                            color = ReinColors.Hairline,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .verticalScroll(scrollState)
                    .padding(start = 13.dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
            ) {
                Text(
                    text = segment.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 21.sp,
                    ),
                    color = ReinColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ToolSegment(
    messageId: String,
    index: Int,
    segment: MessageSegment,
) {
    val hasDetail = segment.detail.isNotBlank()
    var expanded by remember(messageId, index) { mutableStateOf(false) }
    val status = normalizeToolStatus(segment.status)
    val running = status == MessageSegment.TOOL_STATUS_RUNNING
    val failed = status == MessageSegment.TOOL_STATUS_FAILED
    val stateColor = when {
        failed -> ReinColors.Danger
        running -> ReinColors.Mint
        else -> ReinColors.TextTertiary
    }
    val shape = RoundedCornerShape(8.dp)
    val detailScroll = rememberScrollState()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200, easing = LiquidEaseOutSoft),
        label = "toolChevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ReinColors.DeepElevated.copy(alpha = 0.55f))
            .border(1.dp, ReinColors.Hairline, shape)
            .then(
                if (hasDetail) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = !expanded }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolStatusDot(running = running, failed = failed)
            Spacer(Modifier.width(8.dp))
            Text(
                text = toolSegmentLabel(segment),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                color = ReinColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = when {
                    failed -> "失败"
                    running -> "进行中"
                    else -> "完成"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                color = stateColor,
            )
            if (hasDetail) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (failed) "错误" else "结果",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                    color = ReinColors.TextTertiary,
                )
                Spacer(Modifier.width(3.dp))
                SegmentChevron(rotation = chevronRotation)
            }
        }

        if (hasDetail) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(200, easing = LiquidEaseOutSoft),
                    expandFrom = Alignment.Top,
                ) + fadeIn(tween(140)),
                exit = shrinkVertically(
                    animationSpec = tween(150, easing = LiquidEaseOutSoft),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(tween(90)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(max = 220.dp)
                        .verticalScroll(detailScroll),
                ) {
                    Text(
                        text = segment.detail,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = ReinColors.TextTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolStatusDot(running: Boolean, failed: Boolean) {
    val color = when {
        failed -> ReinColors.Danger
        running -> ReinColors.Mint
        else -> ReinColors.TextTertiary.copy(alpha = 0.6f)
    }
    val alpha = if (running) {
        val transition = rememberInfiniteTransition(label = "toolPulse")
        val pulse by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(720, easing = LiquidEaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "toolPulseAlpha",
        )
        pulse
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun SegmentChevron(rotation: Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(11.dp)
            .graphicsLayer { rotationZ = rotation }
    ) {
        val stroke = 1.5.dp.toPx()
        val color = ReinColors.TextTertiary
        drawLine(
            color = color,
            start = Offset(size.width * 0.3f, size.height * 0.16f),
            end = Offset(size.width * 0.68f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.68f, size.height * 0.5f),
            end = Offset(size.width * 0.3f, size.height * 0.84f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun PdfMessageChip(name: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(ReinColors.Surface)
            .border(1.dp, ReinColors.Hairline, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PdfBadge(size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                color = ReinColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            Text(
                text = "附件文档",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                color = ReinColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun ThinkingBlock(
    message: ChatMessage,
    hasContent: Boolean,
    isStreaming: Boolean,
) {
    val stillThinking = isStreaming && !hasContent
    var expanded by remember(message.id) { mutableStateOf(!hasContent) }
    var userToggled by remember(message.id) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200, easing = LiquidEaseOutSoft),
        label = "thinkingChevron",
    )

    LaunchedEffect(hasContent) {
        if (hasContent && !userToggled) expanded = false
    }
    LaunchedEffect(message.reasoning, expanded) {
        if (expanded && stillThinking) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    userToggled = true
                    expanded = !expanded
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentChevron(rotation = chevronRotation)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (stillThinking) PROCESS_LABEL_THINKING_ACTIVE else PROCESS_LABEL_THINKING_DONE,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                color = ReinColors.TextSecondary,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "${message.reasoning.length} 字",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                color = ReinColors.TextTertiary.copy(alpha = 0.8f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (stillThinking) "思考中…" else "已思考",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                color = if (stillThinking) ReinColors.Mint.copy(alpha = 0.8f) else ReinColors.TextTertiary,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(200, easing = LiquidEaseOutSoft),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(140)),
            exit = shrinkVertically(
                animationSpec = tween(150, easing = LiquidEaseOutSoft),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(90)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 5.dp, top = 3.dp, bottom = 2.dp)
                    .heightIn(max = 300.dp)
                    .drawBehind {
                        drawLine(
                            color = ReinColors.Hairline,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .verticalScroll(scrollState)
                    .padding(start = 13.dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
            ) {
                Text(
                    text = message.reasoning,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 21.sp,
                    ),
                    color = ReinColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun UserImageGrid(
    images: List<ChatImage>,
    onOpenImage: (ChatImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.size == 1) {
        val image = images.first()
        val shape = RoundedCornerShape(10.dp)
        Box(
            modifier = modifier
                .aspectRatio(image.aspectRatio.coerceIn(0.94f, 1.72f))
                .clip(shape)
                .border(1.dp, ReinColors.Hairline, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onOpenImage(image) },
        ) {
            AsyncImage(
                model = image.path,
                contentDescription = "对话图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        images.chunked(2).forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                rowImages.forEach { image ->
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(shape)
                            .border(1.dp, ReinColors.Hairline, shape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onOpenImage(image) },
                    ) {
                        AsyncImage(
                            model = image.path,
                            contentDescription = "对话图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (rowImages.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ErrorNotice(message: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ReinColors.Danger.copy(alpha = 0.06f))
            .border(1.dp, ReinColors.Danger.copy(alpha = 0.22f), shape)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = ReinColors.Danger.copy(alpha = 0.9f),
            modifier = Modifier.size(14.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 19.sp),
            color = ReinColors.Danger.copy(alpha = 0.92f),
        )
    }
}

private data class MessageAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun QuietAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
        color = ReinColors.TextTertiary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onClick()
            }
            .padding(horizontal = 5.dp, vertical = 3.dp),
    )
}

@Composable
private fun MessageActionRow(
    actions: List<MessageAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        actions.forEach { action ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .clickable(
                        enabled = action.enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = action.onClick,
                    )
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = ReinColors.TextSecondary.copy(alpha = if (action.enabled) 0.85f else 0.3f),
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp),
                    color = ReinColors.TextSecondary.copy(alpha = if (action.enabled) 0.85f else 0.3f),
                )
            }
        }
    }
}

@Composable
private fun MessageMenuButton(expanded: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(
                if (expanded) ReinColors.TextPrimary.copy(alpha = 0.05f) else Color.Transparent
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = "消息操作",
            tint = ReinColors.TextTertiary.copy(alpha = if (enabled) 0.75f else 0.25f),
            modifier = Modifier.size(16.dp),
        )
    }
}
