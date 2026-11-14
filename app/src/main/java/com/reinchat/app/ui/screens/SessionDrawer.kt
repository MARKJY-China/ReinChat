package com.reinchat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reinchat.app.data.Conversation
import com.reinchat.app.ui.components.GlassDialog
import com.reinchat.app.ui.components.GlassIconButton
import com.reinchat.app.ui.components.GlassInputField
import com.reinchat.app.ui.components.GlassTextButton
import com.reinchat.app.ui.components.LiquidPrimaryButton
import com.reinchat.app.ui.components.SearchField
import com.reinchat.app.ui.components.Wordmark
import com.reinchat.app.ui.components.clickableNoRipple
import com.reinchat.app.ui.components.formatSessionTime
import com.reinchat.app.ui.components.liquidGlass
import com.reinchat.app.ui.components.pressScale
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.LiquidEaseOutSoft
import com.reinchat.app.ui.theme.ReinColors
import java.util.Locale

@Composable
fun SessionDrawerOverlay(
    visible: Boolean,
    conversations: List<Conversation>,
    activeConversationId: String?,
    onClose: () -> Unit,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var expandedMenuId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(visible) {
        if (!visible) {
            query = ""
            expandedMenuId = null
            renameTarget = null
            deleteTarget = null
        }
    }

    BackHandler(enabled = visible && renameTarget == null && deleteTarget == null) {
        onClose()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.47f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    )
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelWidth = minOf(maxWidth * 0.865f, 364.dp)
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.CenterStart),
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(230, easing = LiquidEaseOutSoft),
                ) + fadeIn(tween(120)),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(200, easing = LiquidEaseOutSoft),
                ) + fadeOut(tween(120)),
            ) {
                Column(
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight()
                        .imePadding()
                        .liquidGlass(
                            shape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
                            elevation = 30.dp,
                            topTint = Color(0xDE0C1714),
                            bottomTint = Color(0xEC070C0A),
                            borderAlpha = 1.05f,
                            glowColor = Color.Black.copy(alpha = 0.5f),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp),
                ) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Wordmark(compact = true, modifier = Modifier.weight(1f))
                        GlassIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "关闭会话面板",
                            onClick = onClose,
                            size = 36.dp,
                            iconSize = 17.dp,
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    NewConversationButton(
                        onClick = {
                            onNew()
                            onClose()
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "搜索会话或消息",
                        leading = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = ReinColors.TextTertiary,
                                modifier = Modifier.size(15.dp),
                            )
                        },
                        trailing = if (query.isNotEmpty()) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickableNoRipple { query = "" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "清除搜索",
                                        tint = ReinColors.TextTertiary,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        } else null,
                    )

                    val filtered = remember(conversations, query) {
                        if (query.isBlank()) conversations
                        else {
                            val needle = query.trim().lowercase(Locale.getDefault())
                            conversations.filter { conversation ->
                                conversation.title.lowercase(Locale.getDefault()).contains(needle) ||
                                    conversation.messages.any {
                                        it.content.lowercase(Locale.getDefault()).contains(needle)
                                    }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (query.isBlank()) "最近会话" else "搜索结果",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp),
                            color = ReinColors.TextTertiary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${filtered.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ReinColors.TextTertiary.copy(alpha = 0.6f),
                        )
                    }
                    Spacer(Modifier.height(9.dp))

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (query.isBlank()) "还没有会话\n开始一段新的对话吧" else "没有找到匹配的会话",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ReinColors.TextTertiary.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            items(filtered, key = { it.id }) { conversation ->
                                ConversationRow(
                                    conversation = conversation,
                                    active = conversation.id == activeConversationId,
                                    actionsOpen = conversation.id == expandedMenuId,
                                    onSelect = {
                                        onSelect(conversation.id)
                                        onClose()
                                    },
                                    onToggleActions = {
                                        expandedMenuId = if (expandedMenuId == conversation.id) null
                                        else conversation.id
                                    },
                                    onRename = {
                                        expandedMenuId = null
                                        renameText = conversation.title
                                        renameTarget = conversation
                                    },
                                    onDelete = {
                                        expandedMenuId = null
                                        deleteTarget = conversation
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item { Spacer(Modifier.height(6.dp)) }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickableNoRipple {
                                onClose()
                                onOpenSettings()
                            }
                            .padding(horizontal = 9.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = ReinColors.TextSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "服务商与设置",
                            style = MaterialTheme.typography.labelLarge,
                            color = ReinColors.TextSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "v0.1",
                            style = MaterialTheme.typography.labelMedium,
                            color = ReinColors.TextTertiary.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        GlassDialog(onDismiss = { renameTarget = null }) {
            Text(
                text = "重命名会话",
                style = MaterialTheme.typography.headlineSmall,
                color = ReinColors.TextPrimary,
            )
            Spacer(Modifier.height(17.dp))
            GlassInputField(
                value = renameText,
                onValueChange = { if (it.length <= 80) renameText = it },
                label = "标题",
                placeholder = "输入会话标题",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (renameText.isNotBlank()) {
                        onRename(target.id, renameText)
                        renameTarget = null
                    }
                }),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTextButton(
                    text = "取消",
                    tint = ReinColors.TextSecondary,
                    onClick = { renameTarget = null },
                )
                Spacer(Modifier.width(8.dp))
                LiquidPrimaryButton(
                    text = "保存",
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        onRename(target.id, renameText)
                        renameTarget = null
                    },
                    modifier = Modifier.widthIn(min = 96.dp),
                )
            }
        }
    }

    deleteTarget?.let { target ->
        GlassDialog(onDismiss = { deleteTarget = null }) {
            Text(
                text = "删除这次会话？",
                style = MaterialTheme.typography.headlineSmall,
                color = ReinColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "「${target.title}」及其中的消息、图片将从本机移除，此操作无法撤销。",
                style = MaterialTheme.typography.bodyMedium,
                color = ReinColors.TextSecondary,
            )
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTextButton(
                    text = "取消",
                    tint = ReinColors.TextSecondary,
                    onClick = { deleteTarget = null },
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(ReinColors.Danger.copy(alpha = 0.14f))
                        .border(1.dp, ReinColors.Danger.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
                        .clickableNoRipple {
                            onDelete(target.id)
                            deleteTarget = null
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = ReinColors.Danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewConversationButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        ReinColors.Mint.copy(alpha = 0.17f),
                        ReinColors.Cyan.copy(alpha = 0.065f),
                    )
                )
            )
            .border(1.dp, ReinColors.Mint.copy(alpha = 0.17f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(ReinColors.Mint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = ReinColors.Mint,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "新建对话",
            style = MaterialTheme.typography.titleMedium,
            color = ReinColors.TextPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    active: Boolean,
    actionsOpen: Boolean,
    onSelect: () -> Unit,
    onToggleActions: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (active) Color.White.copy(alpha = 0.075f) else Color.Transparent,
        animationSpec = tween(240, easing = LiquidEase),
        label = "conversationBg",
    )
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(
                if (active) Modifier.border(1.dp, Color.White.copy(alpha = 0.075f), shape)
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelect,
                )
                .padding(start = if (active) 11.dp else 12.dp, end = 6.dp, top = 11.dp, bottom = 9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 1.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (active) {
                        Box(
                            modifier = Modifier
                                .padding(end = 7.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(ReinColors.Mint)
                        )
                    }
                    if (conversation.cloud) {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = "云端会话",
                            tint = ReinColors.Cyan.copy(alpha = 0.65f),
                            modifier = Modifier
                                .padding(end = 5.dp)
                                .size(12.dp),
                        )
                    }
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (active) ReinColors.TextPrimary else ReinColors.TextPrimary.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatSessionTime(conversation.updatedAt),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                        color = ReinColors.TextTertiary.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = conversation.preview,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = ReinColors.TextTertiary.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickableNoRipple(onToggleActions),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = "会话操作",
                    tint = ReinColors.TextTertiary.copy(alpha = if (actionsOpen) 0.9f else 0.52f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = actionsOpen,
            enter = fadeIn(tween(150)) + expandVertically(
                tween(220, easing = LiquidEaseOutSoft),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                InlineAction(
                    label = "重命名",
                    icon = Icons.Rounded.Edit,
                    tint = ReinColors.TextSecondary,
                    onClick = onRename,
                    modifier = Modifier.weight(1f),
                )
                InlineAction(
                    label = "删除",
                    icon = Icons.Rounded.DeleteOutline,
                    tint = ReinColors.Danger.copy(alpha = 0.9f),
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun InlineAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickableNoRipple(onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}
