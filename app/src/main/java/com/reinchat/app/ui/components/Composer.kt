package com.reinchat.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.reinchat.app.data.ChatImage
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.LiquidEaseOutSoft
import com.reinchat.app.ui.theme.ReinColors

@Composable
fun Composer(
    draft: String,
    attachments: List<ChatImage>,
    isStreaming: Boolean,
    importingImage: Boolean,
    editing: Boolean,
    providerLabel: String?,
    webSearch: Boolean,
    searchAvailable: Boolean,
    relayMode: Boolean,
    onToggleWebSearch: () -> Unit,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onAddImage: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCancelEdit: () -> Unit,
    onProviderClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val borderGlow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(380, easing = LiquidEase),
        label = "composerGlow",
    )
    val composerShape = RoundedCornerShape(29.dp)
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        if (editing) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val glowCenter = Offset(size.width * 0.78f, size.height * 0.68f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ReinColors.Mint.copy(alpha = 0.10f + borderGlow * 0.05f),
                            ReinColors.Cyan.copy(alpha = 0.035f),
                            Color.Transparent,
                        ),
                        center = glowCenter,
                        radius = size.width * 0.72f,
                    ),
                    radius = size.width * 0.72f,
                    center = glowCenter,
                )
            }
            .animateContentSize(animationSpec = tween(300, easing = LiquidEaseOutSoft))
            .liquidGlass(
                shape = composerShape,
                elevation = 24.dp,
                topTint = Color.White.copy(alpha = 0.105f + borderGlow * 0.03f),
                bottomTint = Color.White.copy(alpha = 0.038f),
                glowColor = ReinColors.Mint.copy(alpha = 0.16f + borderGlow * 0.10f),
            )
            .then(
                if (borderGlow > 0.01f) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                ReinColors.Mint.copy(alpha = 0.06f * borderGlow),
                                ReinColors.Mint.copy(alpha = 0.34f * borderGlow),
                                ReinColors.Cyan.copy(alpha = 0.10f * borderGlow),
                            )
                        ),
                        shape = composerShape,
                    )
                } else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        AnimatedVisibility(
            visible = editing,
            enter = fadeIn(tween(160)) + expandVertically(tween(240, easing = LiquidEaseOutSoft)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(180)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = ReinColors.Mint,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "正在编辑之前的提问",
                    style = MaterialTheme.typography.labelMedium,
                    color = ReinColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCancelEdit,
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelMedium,
                        color = ReinColors.Mint,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = attachments.isNotEmpty() || importingImage,
            enter = fadeIn(tween(180)) + expandVertically(
                animationSpec = tween(280, easing = LiquidEaseOutSoft),
                expandFrom = Alignment.Bottom,
            ),
            exit = fadeOut(tween(120)) + shrinkVertically(
                animationSpec = tween(200, easing = LiquidEaseOutSoft),
                shrinkTowards = Alignment.Bottom,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { image ->
                    AttachmentThumbnail(
                        image = image,
                        onRemove = { onRemoveAttachment(image.id) },
                    )
                }
                if (importingImage) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color.White.copy(alpha = 0.045f))
                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            color = ReinColors.Mint.copy(alpha = 0.75f),
                            strokeWidth = 1.5.dp,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            AddImageButton(
                enabled = !importingImage,
                onClick = onAddImage,
            )
            Spacer(Modifier.width(2.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 7.dp, bottom = 7.dp, start = 3.dp, end = 4.dp)
                    .heightIn(min = 24.dp, max = 150.dp),
            ) {
                if (draft.isEmpty()) {
                    Text(
                        text = "给 ReinChat 发消息…",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.5.sp,
                            lineHeight = 23.sp,
                        ),
                        color = ReinColors.TextTertiary.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            focused = focusState.isFocused
                        },
                    textStyle = TextStyle(
                        color = ReinColors.TextPrimary,
                        fontSize = 15.5.sp,
                        lineHeight = 23.sp,
                        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                    ),
                    cursorBrush = Brush.verticalGradient(listOf(ReinColors.Mint, ReinColors.Cyan)),
                    minLines = 1,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                )
            }

            Spacer(Modifier.width(5.dp))
            SendButton(
                isStreaming = isStreaming,
                enabled = canSend,
                onSend = onSend,
                onStop = onStop,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 7.dp, end = 6.dp, top = 1.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderChip(label = providerLabel, onClick = onProviderClick)
            if (searchAvailable) {
                Spacer(Modifier.width(5.dp))
                SearchChip(active = webSearch, onClick = onToggleWebSearch)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (isStreaming) ReinColors.Mint.copy(alpha = 0.9f)
                        else ReinColors.Mint.copy(alpha = 0.36f)
                    )
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (isStreaming) "正在生成" else if (relayMode) "官方中转" else "端侧直连",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                color = ReinColors.TextTertiary.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun AttachmentThumbnail(image: ChatImage, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(58.dp)) {
        AsyncImage(
            model = image.path,
            contentDescription = "待发送图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(15.dp))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(15.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(19.dp)
                .clip(CircleShape)
                .background(Color(0xCC07100D))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "移除图片",
                tint = ReinColors.TextPrimary,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun AddImageButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.AddPhotoAlternate,
            contentDescription = "添加图片",
            tint = ReinColors.TextSecondary.copy(alpha = if (enabled) 0.86f else 0.3f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SendButton(
    isStreaming: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val active = isStreaming || enabled
    val glowAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(360, easing = LiquidEase),
        label = "sendGlow",
    )

    Box(
        modifier = Modifier
            .size(50.dp)
            .pressScale(interaction, 0.9f)
            .drawBehind {
                if (glowAlpha > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ReinColors.Mint.copy(alpha = 0.20f * glowAlpha),
                                ReinColors.Cyan.copy(alpha = 0.08f * glowAlpha),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = size.minDimension * 0.6f,
                        ),
                        radius = size.minDimension * 0.6f,
                        center = center,
                    )
                }
            }
            .clip(CircleShape)
            .background(
                if (isStreaming) {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.06f))
                    )
                } else if (enabled) {
                    Brush.linearGradient(
                        0f to Color(0xFFF0FFF8),
                        0.32f to ReinColors.Mint,
                        1f to Color(0xFF8CE4F8),
                    )
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.035f))
                    )
                }
            )
            .border(
                width = 1.dp,
                color = if (active) Color.White.copy(alpha = 0.36f) else Color.White.copy(alpha = 0.09f),
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isStreaming) onStop() else if (enabled) onSend()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isStreaming,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(tween(220, easing = LiquidEase), initialScale = 0.65f))
                    .togetherWith(
                        fadeOut(tween(100)) + scaleOut(tween(160, easing = LiquidEaseOutSoft), targetScale = 0.65f)
                    )
            },
            label = "sendMorph",
        ) { stopping ->
            if (stopping) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(RoundedCornerShape(3.5.dp))
                        .background(ReinColors.TextPrimary)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = "发送",
                    tint = if (enabled) Color(0xFF042019) else ReinColors.TextTertiary.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(21.dp)
                        .graphicsLayer {
                            alpha = if (enabled) 1f else 0.6f
                            translationY = if (enabled) 0f else 2.dp.toPx()
                        },
                )
            }
        }
    }
}

@Composable
private fun SearchChip(active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (active) ReinColors.Mint.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.045f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Language,
            contentDescription = "联网搜索",
            tint = if (active) ReinColors.Mint else ReinColors.TextTertiary,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "联网",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
            color = if (active) ReinColors.Mint else ReinColors.TextTertiary,
        )
    }
}

@Composable
private fun ProviderChip(label: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidOrb(size = 13.dp, letter = false)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label ?: "配置服务商",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = if (label == null) ReinColors.Warm.copy(alpha = 0.9f) else ReinColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = ReinColors.TextTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}
