package com.reinchat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.ReinColors
import kotlinx.coroutines.delay

@Composable
fun EmptyChat(
    providerConfigured: Boolean,
    providerLabel: String?,
    cloudAvailable: Boolean,
    cloudEnabled: Boolean,
    onCloudChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onConfigure: () -> Unit,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        appeared = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(420, easing = LiquidEase)) +
                slideInVertically(tween(420, easing = LiquidEase)) { it / 6 },
        ) {
            Column(modifier = Modifier.widthIn(max = 560.dp)) {
                Text(
                    text = "A QUIET PLACE TO THINK",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 9.5.sp,
                        letterSpacing = 2.4.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = ReinColors.TextTertiary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "今天想聊什么？",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 34.sp,
                        lineHeight = 41.sp,
                        letterSpacing = (-0.9).sp,
                    ),
                    color = ReinColors.TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "一个安静、克制的 AI 对话空间。选择模型，或者直接开始。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReinColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(26.dp))

        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(460, delayMillis = 90, easing = LiquidEase)) +
                slideInVertically(tween(460, delayMillis = 90, easing = LiquidEase)) { it / 8 },
        ) {
            CloudSaveToggle(
                available = cloudAvailable,
                enabled = cloudEnabled,
                onChange = onCloudChange,
                onLoginClick = onLoginClick,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }

        Spacer(Modifier.height(22.dp))

        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(460, delayMillis = 160, easing = LiquidEase)) +
                slideInVertically(tween(460, delayMillis = 160, easing = LiquidEase)) { it / 8 },
        ) {
            if (!providerConfigured) {
                ConnectProviderCard(
                    onConfigure = onConfigure,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            } else {
                Column(modifier = Modifier.widthIn(max = 560.dp)) {
                    Text(
                        text = "试试这样开始",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = ReinColors.TextTertiary,
                    )
                    Spacer(Modifier.height(10.dp))
                    SuggestionRow(
                        first = "解释一个复杂概念",
                        second = "润色一段文字",
                        onClick = onSuggestion,
                    )
                    Spacer(Modifier.height(6.dp))
                    SuggestionRow(
                        first = "写一个产品方案",
                        second = "策划一次周末出走",
                        onClick = onSuggestion,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(ReinColors.Mint.copy(alpha = 0.8f))
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = providerLabel ?: "已连接服务商",
                            style = MaterialTheme.typography.labelMedium,
                            color = ReinColors.TextTertiary,
                        )
                        Text(
                            text = "  ·  点按消息可编辑或回退",
                            style = MaterialTheme.typography.labelMedium,
                            color = ReinColors.TextTertiary.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CloudSaveToggle(
    available: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ReinColors.Surface)
            .border(1.dp, ReinColors.Hairline, shape)
            .then(
                if (!available) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onLoginClick,
                    )
                } else Modifier
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (enabled) ReinColors.AccentSoft else ReinColors.DeepElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Cloud,
                contentDescription = null,
                tint = if (enabled) ReinColors.Mint else ReinColors.TextTertiary,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "聊天记录保存云端",
                style = MaterialTheme.typography.labelLarge,
                color = ReinColors.TextPrimary.copy(alpha = if (available) 0.92f else 0.55f),
            )
            Text(
                text = when {
                    !available -> "登录后可开启跨设备同步"
                    enabled -> "已开启，可跨设备继续"
                    else -> "本会话仅保存在此设备，发送后不可再开启"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp, lineHeight = 14.sp),
                color = ReinColors.TextTertiary,
            )
        }
        if (!available) {
            GlassTextButton(text = "登录", onClick = onLoginClick)
        } else {
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ReinColors.OnAccent,
                    checkedTrackColor = ReinColors.Mint,
                    checkedBorderColor = ReinColors.Mint,
                    uncheckedThumbColor = ReinColors.TextTertiary,
                    uncheckedTrackColor = ReinColors.DeepElevated,
                    uncheckedBorderColor = ReinColors.Hairline,
                ),
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    first: String,
    second: String,
    onClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SuggestionChip(text = first, onClick = { onClick(first) }, modifier = Modifier.weight(1f))
        SuggestionChip(text = second, onClick = { onClick(second) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = modifier
            .pressScale(interaction, 0.98f)
            .clip(shape)
            .background(ReinColors.Surface)
            .border(1.dp, ReinColors.Hairline, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            color = ReinColors.TextSecondary,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "→",
            style = MaterialTheme.typography.labelLarge,
            color = ReinColors.TextTertiary,
        )
    }
}

@Composable
private fun ConnectProviderCard(
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ReinColors.Surface)
            .border(1.dp, ReinColors.Hairline, shape)
            .padding(18.dp),
    ) {
        Text(
            text = "SETUP",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = ReinColors.TextTertiary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "先连接一个模型",
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 18.sp),
            color = ReinColors.TextPrimary,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "支持 Responses 与 Chat Completions。填入 Base URL、模型名称与 API Key，即可开始对话。",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = ReinColors.TextSecondary,
        )
        Spacer(Modifier.height(15.dp))
        LiquidPrimaryButton(
            text = "配置 AI 服务商",
            onClick = onConfigure,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(11.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = ReinColors.TextTertiary,
                modifier = Modifier.size(10.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "本机加密存储 · 请求直连服务商",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                color = ReinColors.TextTertiary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
