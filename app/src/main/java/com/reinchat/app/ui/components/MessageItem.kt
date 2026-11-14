package com.reinchat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    modifier: Modifier = Modifier,
) {
    val entrance = remember(message.id) { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(message.id) {
        if (animateIn) {
            entrance.animateTo(1f, animationSpec = tween(460, easing = LiquidEase))
        }
    }
    val contentAlpha = entrance.value
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = (1f - contentAlpha) * 16.dp.toPx()
            }
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
        val maxBubble = minOf(maxWidth * 0.84f, 430.dp)
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
                    .liquidGlass(
                        shape = RoundedCornerShape(
                            topStart = 23.dp,
                            topEnd = 23.dp,
                            bottomEnd = 7.dp,
                            bottomStart = 23.dp,
                        ),
                        topTint = ReinColors.UserBubbleTop,
                        bottomTint = ReinColors.UserBubbleBottom,
                        borderAlpha = 1.15f,
                        elevation = 4.dp,
                    )
                    .clickableNoRipple {
                        if (!isStreaming) onToggleActions()
                    }
                    .padding(if (message.images.isNotEmpty()) 5.dp else 0.dp),
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
                            horizontal = if (message.images.isNotEmpty()) 13.dp else 16.dp,
                            vertical = if (message.images.isNotEmpty()) 11.dp else 12.dp,
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                        ),
                        color = ReinColors.TextPrimary,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 5.dp, end = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatClock(message.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = ReinColors.TextTertiary.copy(alpha = 0.72f),
                )
                Spacer(Modifier.width(2.dp))
                MessageMenuButton(
                    expanded = actionsOpen,
                    enabled = !isStreaming,
                    onClick = onToggleActions,
                )
            }

            AnimatedVisibility(
                visible = actionsOpen,
                enter = fadeIn(tween(180)) + expandVertically(
                    animationSpec = tween(280, easing = LiquidEaseOutSoft),
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(tween(120)) + shrinkVertically(
                    animationSpec = tween(180, easing = LiquidEaseOutSoft),
                    shrinkTowards = Alignment.Top,
                ),
            ) {
                MessageActions(
                    modifier = Modifier.padding(top = 2.dp, end = 2.dp),
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
                                label = "回退到此处",
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
private fun ThinkingBlock(
    message: ChatMessage,
    hasContent: Boolean,
    isStreaming: Boolean,
) {
    val stillThinking = isStreaming && !hasContent
    var expanded by remember(message.id) { mutableStateOf(!hasContent) }
    var userToggled by remember(message.id) { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(hasContent) {
        if (hasContent && !userToggled) expanded = false
    }
    LaunchedEffect(message.reasoning, expanded) {
        if (expanded && stillThinking) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val shape = RoundedCornerShape(14.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            ReinColors.Mint.copy(alpha = 0.055f),
                            Color.White.copy(alpha = 0.022f),
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.065f), shape)
                .clickableNoRipple {
                    userToggled = true
                    expanded = !expanded
                }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = ReinColors.Mint.copy(alpha = 0.78f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "思考过程",
                style = MaterialTheme.typography.labelLarge,
                color = ReinColors.TextSecondary,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (stillThinking) "思考中…" else "已思考",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                color = if (stillThinking) ReinColors.Mint.copy(alpha = 0.7f)
                else ReinColors.TextTertiary.copy(alpha = 0.65f),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起思考过程" else "展开思考过程",
                tint = ReinColors.TextTertiary,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(260, easing = LiquidEaseOutSoft),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(160)),
            exit = shrinkVertically(
                animationSpec = tween(180, easing = LiquidEaseOutSoft),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(100)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
                    .heightIn(max = 300.dp)
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.13f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), shape)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                MarkdownText(
                    markdown = message.reasoning,
                    modifier = Modifier.fillMaxWidth(),
                    streaming = stillThinking,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickableNoRipple {
                    if (!isStreaming) onToggleActions()
                }
        ) {
            Row(
                modifier = Modifier.padding(bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidOrb(size = 22.dp, letter = false)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = message.model.ifBlank { "ReinChat" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ReinColors.TextPrimary.copy(alpha = 0.92f),
                )
                if (isStreaming) {
                    Spacer(Modifier.width(10.dp))
                    ThinkingDots()
                }
            }

            if (message.reasoning.isNotBlank()) {
                ThinkingBlock(
                    message = message,
                    hasContent = hasContent,
                    isStreaming = isStreaming,
                )
                if (hasContent) Spacer(Modifier.height(11.dp))
            }

            when {
                hasContent -> {
                    MarkdownText(
                        markdown = message.content,
                        modifier = Modifier.fillMaxWidth(),
                        streaming = isStreaming,
                    )
                    if (message.status == MessageStatus.ERROR) {
                        ErrorNotice(
                            message = message.error ?: "生成中断",
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                isStreaming -> {
                    if (message.reasoning.isBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ThinkingDots()
                            Spacer(Modifier.width(10.dp))
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
        }

        Row(
            modifier = Modifier.padding(top = 7.dp, start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatClock(message.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = ReinColors.TextTertiary.copy(alpha = 0.72f),
            )
            if (message.status == MessageStatus.STOPPED && hasContent) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(ReinColors.TextTertiary.copy(alpha = 0.5f))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "已停止",
                    style = MaterialTheme.typography.labelMedium,
                    color = ReinColors.TextTertiary,
                )
            }
            Spacer(Modifier.width(2.dp))
            MessageMenuButton(
                expanded = actionsOpen,
                enabled = !isStreaming,
                onClick = onToggleActions,
            )
        }

        AnimatedVisibility(
            visible = actionsOpen,
            enter = fadeIn(tween(180)) + expandVertically(
                animationSpec = tween(280, easing = LiquidEaseOutSoft),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(tween(120)) + shrinkVertically(
                animationSpec = tween(180, easing = LiquidEaseOutSoft),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            MessageActions(
                modifier = Modifier.padding(top = 2.dp, start = 2.dp),
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
                            label = "回退到此处",
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
private fun UserImageGrid(
    images: List<ChatImage>,
    onOpenImage: (ChatImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.size == 1) {
        val image = images.first()
        Box(
            modifier = modifier
                .aspectRatio(image.aspectRatio.coerceIn(0.94f, 1.72f))
                .clip(RoundedCornerShape(19.dp))
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        images.chunked(2).forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rowImages.forEach { image ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(17.dp))
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                topTint = ReinColors.Danger.copy(alpha = 0.12f),
                bottomTint = ReinColors.Danger.copy(alpha = 0.055f),
                borderAlpha = 1.4f,
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = ReinColors.Danger.copy(alpha = 0.9f),
            modifier = Modifier.size(16.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
            color = ReinColors.Danger.copy(alpha = 0.92f),
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
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
private fun MessageActions(
    actions: List<MessageAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.liquidGlass(
            shape = RoundedCornerShape(50),
            topTint = Color.White.copy(alpha = 0.075f),
            bottomTint = Color.White.copy(alpha = 0.025f),
            elevation = 8.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        enabled = action.enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = action.onClick,
                    )
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = ReinColors.TextSecondary.copy(alpha = if (action.enabled) 1f else 0.3f),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = ReinColors.TextSecondary.copy(alpha = if (action.enabled) 1f else 0.3f),
                )
            }
        }
    }
}

@Composable
private fun MessageMenuButton(expanded: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (expanded) Color.White.copy(alpha = 0.06f) else Color.Transparent
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
