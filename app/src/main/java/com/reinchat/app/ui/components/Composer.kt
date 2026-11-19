package com.reinchat.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

/**
 * Flat composer dock: a hairline field on paper, compact text triggers for the
 * provider / search settings, and a single ink send square.
 */
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
    val borderColor by animateColorAsState(
        targetValue = if (focused) ReinColors.HairlineStrong else ReinColors.Hairline,
        animationSpec = tween(180, easing = LiquidEase),
        label = "composerBorder",
    )
    val composerShape = RoundedCornerShape(14.dp)
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
            .animateContentSize(animationSpec = tween(220, easing = LiquidEaseOutSoft))
            .clip(composerShape)
            .background(ReinColors.Surface)
            .border(1.dp, borderColor, composerShape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        AnimatedVisibility(
            visible = editing,
            enter = fadeIn(tween(140)) + expandVertically(tween(200, easing = LiquidEaseOutSoft)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(160)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 3.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = ReinColors.Mint,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "正在编辑之前的提问",
                    style = MaterialTheme.typography.labelMedium,
                    color = ReinColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelMedium,
                    color = ReinColors.Mint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCancelEdit,
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = attachments.isNotEmpty() || importingImage,
            enter = fadeIn(tween(160)) + expandVertically(
                animationSpec = tween(240, easing = LiquidEaseOutSoft),
                expandFrom = Alignment.Bottom,
            ),
            exit = fadeOut(tween(100)) + shrinkVertically(
                animationSpec = tween(180, easing = LiquidEaseOutSoft),
                shrinkTowards = Alignment.Bottom,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ReinColors.DeepElevated)
                            .border(1.dp, ReinColors.Hairline, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = ReinColors.Mint.copy(alpha = 0.8f),
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
                    .heightIn(min = 22.dp, max = 150.dp),
            ) {
                if (draft.isEmpty()) {
                    Text(
                        text = "输入消息…",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 14.5.sp,
                            lineHeight = 22.sp,
                        ),
                        color = ReinColors.Placeholder,
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
                        fontSize = 14.5.sp,
                        lineHeight = 22.sp,
                        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                    ),
                    cursorBrush = SolidColor(ReinColors.Mint),
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
                .padding(start = 3.dp, end = 3.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderChip(label = providerLabel, onClick = onProviderClick)
            if (searchAvailable) {
                Spacer(Modifier.width(3.dp))
                SearchChip(active = webSearch, onClick = onToggleWebSearch)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (isStreaming) ReinColors.Mint.copy(alpha = 0.9f)
                        else ReinColors.TextTertiary.copy(alpha = 0.4f)
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isStreaming) "正在生成" else if (relayMode) "官方中转" else "端侧直连",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                color = ReinColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun AttachmentThumbnail(image: ChatImage, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(52.dp)) {
        AsyncImage(
            model = image.path,
            contentDescription = "待发送图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, ReinColors.Hairline, RoundedCornerShape(8.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(17.dp)
                .clip(CircleShape)
                .background(ReinColors.TextPrimary)
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
                tint = ReinColors.OnInk,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

@Composable
private fun AddImageButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .pressScale(interaction, 0.92f)
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
            tint = ReinColors.TextSecondary.copy(alpha = if (enabled) 0.85f else 0.3f),
            modifier = Modifier.size(19.dp),
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
    val background by animateColorAsState(
        targetValue = if (active) ReinColors.TextPrimary else ReinColors.TextPrimary.copy(alpha = 0.10f),
        animationSpec = tween(160, easing = LiquidEase),
        label = "sendBg",
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .pressScale(interaction, 0.92f)
            .clip(RoundedCornerShape(9.dp))
            .background(background)
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
                (fadeIn(tween(120)) + scaleIn(tween(160, easing = LiquidEase), initialScale = 0.7f))
                    .togetherWith(
                        fadeOut(tween(90)) + scaleOut(tween(120, easing = LiquidEaseOutSoft), targetScale = 0.7f)
                    )
            },
            label = "sendMorph",
        ) { stopping ->
            if (stopping) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ReinColors.OnInk)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = "发送",
                    tint = if (enabled) ReinColors.OnInk else ReinColors.TextTertiary,
                    modifier = Modifier
                        .size(19.dp)
                        .graphicsLayer {
                            alpha = if (enabled) 1f else 0.7f
                            translationY = if (enabled) 0f else 1.dp.toPx()
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
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) ReinColors.AccentSoft else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
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
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            color = if (active) ReinColors.Mint else ReinColors.TextTertiary,
        )
    }
}

@Composable
private fun ProviderChip(label: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label ?: "配置服务商",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = if (label == null) ReinColors.Mint else ReinColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 190.dp),
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
