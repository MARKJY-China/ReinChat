package com.reinchat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.reinchat.app.data.ChatImage
import com.reinchat.app.data.ChatMessage
import com.reinchat.app.data.MessageRole
import com.reinchat.app.data.MessageStatus
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.LiquidEaseOutSoft
import com.reinchat.app.ui.theme.ReinColors

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
                    .padding(if (message.images.isNotEmpty()) 4.dp else 0.dp),
            ) {
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
                            horizontal = if (message.images.isNotEmpty()) 12.dp else 15.dp,
                            vertical = if (message.images.isNotEmpty()) 10.dp else 11.dp,
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
    canRewind: Boolean,
    actionsOpen: Boolean,
    onToggleActions: () -> Unit,
    onCloseActions: () -> Unit,
    onCopy: (ChatMessage) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onRewind: (ChatMessage) -> Unit,
) {
    val hasContent = message.content.isNotBlank()

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
            if (message.status == MessageStatus.STOPPED && hasContent) {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "已停止",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                    color = ReinColors.TextTertiary,
                )
            }
            if (isStreaming) {
                Spacer(Modifier.width(8.dp))
                ThinkingDots()
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

        if (message.reasoning.isNotBlank()) {
            ThinkingBlock(
                message = message,
                hasContent = hasContent,
                isStreaming = isStreaming,
            )
            if (hasContent) Spacer(Modifier.height(10.dp))
        }

        when {
            hasContent -> {
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

            isStreaming -> {
                if (message.reasoning.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThinkingDots()
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = "正在思考",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ReinColors.TextTertiary,
                        )
                    }
                }
            }

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
            Canvas(
                modifier = Modifier
                    .size(11.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
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
            Spacer(Modifier.width(6.dp))
            Text(
                text = "思考过程",
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
