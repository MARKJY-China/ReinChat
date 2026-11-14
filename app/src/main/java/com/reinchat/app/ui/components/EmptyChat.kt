package com.reinchat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.LiquidEaseInOut
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
        delay(80)
        appeared = true
    }
    val floatTransition = rememberInfiniteTransition(label = "heroFloat")
    val float by floatTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LiquidEaseInOut),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "float",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(22.dp))
        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(720, easing = LiquidEase)) +
                slideInVertically(tween(720, easing = LiquidEase)) { it / 5 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(132.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        ReinColors.Mint.copy(alpha = 0.14f),
                                        ReinColors.Cyan.copy(alpha = 0.05f),
                                        Color.Transparent,
                                    )
                                )
                            )
                    )
                    LiquidOrb(
                        size = 76.dp,
                        letter = true,
                        modifier = Modifier.graphicsLayer { translationY = float.dp.toPx() },
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "R E I N C H A T",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 3.2.sp,
                        fontSize = 10.sp,
                    ),
                    color = ReinColors.Mint.copy(alpha = 0.68f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = buildAnnotatedString {
                        append("让思绪，")
                        withStyle(
                            SpanStyle(
                                brush = Brush.linearGradient(
                                    listOf(ReinColors.Mint, ReinColors.Cyan, ReinColors.Violet)
                                )
                            )
                        ) {
                            append("发生。")
                        }
                    },
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 38.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 0.2.sp,
                    ),
                    color = ReinColors.TextPrimary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (providerConfigured) {
                        "一个安静、顺滑的 AI 对话空间"
                    } else {
                        "一个安静、顺滑的 AI 对话空间"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReinColors.TextSecondary.copy(alpha = 0.72f),
                )
            }
        }

        Spacer(Modifier.height(27.dp))

        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(600, delayMillis = 170, easing = LiquidEase)) +
                slideInVertically(tween(600, delayMillis = 170, easing = LiquidEase)) { it / 4 },
        ) {
            CloudSaveToggle(
                available = cloudAvailable,
                enabled = cloudEnabled,
                onChange = onCloudChange,
                onLoginClick = onLoginClick,
                modifier = Modifier.widthIn(max = 440.dp),
            )
        }

        Spacer(Modifier.height(22.dp))

        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(600, delayMillis = 240, easing = LiquidEase)) +
                slideInVertically(tween(600, delayMillis = 240, easing = LiquidEase)) { it / 4 },
        ) {
            if (!providerConfigured) {
                ConnectProviderCard(
                    onConfigure = onConfigure,
                    modifier = Modifier.widthIn(max = 440.dp),
                )
            } else {
                Column(
                    modifier = Modifier.widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "试试这样开始",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                        color = ReinColors.TextTertiary,
                    )
                    Spacer(Modifier.height(13.dp))
                    SuggestionRow(
                        first = "解释一个复杂概念",
                        second = "润色一段文字",
                        onClick = onSuggestion,
                    )
                    Spacer(Modifier.height(8.dp))
                    SuggestionRow(
                        first = "写一个产品方案",
                        second = "策划一次周末出走",
                        onClick = onSuggestion,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(ReinColors.Mint.copy(alpha = 0.55f))
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = providerLabel ?: "已连接服务商",
                            style = MaterialTheme.typography.labelMedium,
                            color = ReinColors.TextTertiary.copy(alpha = 0.75f),
                        )
                        Text(
                            text = "  ·  点按气泡或 ··· 可编辑或回退",
                            style = MaterialTheme.typography.labelMedium,
                            color = ReinColors.TextTertiary.copy(alpha = 0.55f),
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(19.dp),
                topTint = Color.White.copy(alpha = 0.065f),
                bottomTint = Color.White.copy(alpha = 0.022f),
            )
            .then(
                if (!available) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onLoginClick,
                    )
                } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) ReinColors.Mint.copy(alpha = 0.10f)
                    else Color.White.copy(alpha = 0.04f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Cloud,
                contentDescription = null,
                tint = if (enabled) ReinColors.Mint else ReinColors.TextTertiary.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "聊天记录保存云端",
                style = MaterialTheme.typography.labelLarge,
                color = ReinColors.TextPrimary.copy(alpha = if (available) 0.9f else 0.55f),
            )
            Text(
                text = when {
                    !available -> "登录后可开启跨设备同步"
                    enabled -> "开启后不可关闭，可跨设备继续"
                    else -> "本会话仅保存在此设备，发送后不可再开启"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                color = ReinColors.TextTertiary.copy(alpha = 0.8f),
            )
        }
        if (!available) {
            GlassTextButton(text = "登录", onClick = onLoginClick)
        } else {
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF052019),
                    checkedTrackColor = ReinColors.Mint,
                    checkedBorderColor = ReinColors.Mint,
                    uncheckedThumbColor = ReinColors.TextTertiary,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                    uncheckedBorderColor = Color.White.copy(alpha = 0.12f),
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuggestionChip(text = first, onClick = { onClick(first) }, modifier = Modifier.weight(1f))
        SuggestionChip(text = second, onClick = { onClick(second) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(interaction, 0.97f)
            .liquidGlass(
                shape = RoundedCornerShape(18.dp),
                topTint = Color.White.copy(alpha = 0.088f),
                bottomTint = Color.White.copy(alpha = 0.028f),
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = ReinColors.Mint.copy(alpha = 0.65f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = ReinColors.TextSecondary,
            maxLines = 2,
        )
    }
}

@Composable
private fun ConnectProviderCard(
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(28.dp),
                topTint = Color.White.copy(alpha = 0.098f),
                bottomTint = Color.White.copy(alpha = 0.032f),
                glowColor = ReinColors.Mint.copy(alpha = 0.10f),
                elevation = 10.dp,
            )
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ReinColors.Mint.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = ReinColors.Mint,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(
                    text = "先连接一个模型",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ReinColors.TextPrimary,
                )
                Text(
                    text = "支持 Responses 与 Chat Completions",
                    style = MaterialTheme.typography.labelMedium,
                    color = ReinColors.TextTertiary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "填入 Base URL、模型名称与 API Key，即可开始对话。密钥默认只留在本机；登录后可选择把部分服务商和云端会话同步到账号。",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = ReinColors.TextSecondary.copy(alpha = 0.86f),
        )
        Spacer(Modifier.height(17.dp))
        LiquidPrimaryButton(
            text = "配置 AI 服务商",
            onClick = onConfigure,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = ReinColors.TextTertiary.copy(alpha = 0.64f),
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "本机加密存储 · 请求直连服务商",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                color = ReinColors.TextTertiary.copy(alpha = 0.62f),
            )
        }
    }
}
