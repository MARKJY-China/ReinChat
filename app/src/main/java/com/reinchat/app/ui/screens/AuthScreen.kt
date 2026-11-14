package com.reinchat.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reinchat.app.data.CloudAccount
import com.reinchat.app.data.DEFAULT_CLOUD_SERVER
import com.reinchat.app.ui.components.GlassIconButton
import com.reinchat.app.ui.components.GlassInputField
import com.reinchat.app.ui.components.LiquidPrimaryButton
import com.reinchat.app.ui.components.Wordmark
import com.reinchat.app.ui.components.clickableNoRipple
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.ReinColors

@Composable
fun AuthScreen(
    initialServerUrl: String,
    account: CloudAccount?,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    onLogin: (String, String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var serverUrl by rememberSaveable { mutableStateOf(initialServerUrl.ifBlank { DEFAULT_CLOUD_SERVER }) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(account) {
        if (account != null) onAuthenticated()
    }

    fun submit() {
        if (busy) return
        if (registerMode) onRegister(serverUrl, username, password)
        else onLogin(serverUrl, username, password)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF081511), Color(0xFF040807))
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(ReinColors.Mint.copy(alpha = 0.16f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.08f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.08f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(ReinColors.Violet.copy(alpha = 0.09f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.9f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.9f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    size = 40.dp,
                    iconSize = 18.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (registerMode) "创建账号" else "登录 ReinChat",
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                        color = ReinColors.TextPrimary,
                    )
                    Text(
                        text = "多端同步你的对话与服务商",
                        style = MaterialTheme.typography.labelMedium,
                        color = ReinColors.TextTertiary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
            ) {
                Spacer(Modifier.height(18.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        ReinColors.Mint.copy(alpha = 0.24f),
                                        ReinColors.Cyan.copy(alpha = 0.10f),
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = null,
                            tint = ReinColors.Mint,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Wordmark(compact = true)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = "登录后，云端会话可在其他设备继续",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReinColors.TextTertiary,
                    )
                }

                Spacer(Modifier.height(25.dp))
                AuthModeSelector(
                    registerMode = registerMode,
                    onModeChange = {
                        registerMode = it
                        onClearError()
                    },
                )
                Spacer(Modifier.height(22.dp))

                GlassInputField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = "服务器地址",
                    placeholder = DEFAULT_CLOUD_SERVER,
                    supportingText = "填 reinchat.markjy.com 或完整的 /api/v1 地址",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(15.dp))

                GlassInputField(
                    value = username,
                    onValueChange = {
                        username = it
                        onClearError()
                    },
                    label = "账号",
                    placeholder = "3–32 位，支持中英文与 _ - .",
                    supportingText = if (registerMode) "账号名全站唯一，不区分大小写" else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(15.dp))

                GlassInputField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearError()
                    },
                    label = "密码",
                    placeholder = if (registerMode) "至少 8 位" else "输入密码",
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    supportingText = if (registerMode) "密码会经 bcrypt 哈希后保存在服务器" else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailing = {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.White.copy(alpha = 0.045f))
                                .clickableNoRipple { showPassword = !showPassword },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff
                                else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                                tint = ReinColors.TextTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                )

                AnimatedContent(
                    targetState = error,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "authError",
                ) { message ->
                    if (message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ReinColors.Danger.copy(alpha = 0.95f),
                            modifier = Modifier.padding(top = 14.dp, start = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    text = "登录时使用的账号密码只发送到你填写的服务器。未标记“仅本地”的服务商配置会同步到该账号；所有请求都经 HTTPS（或你填写的明文地址）传输。",
                    style = MaterialTheme.typography.labelMedium.copy(lineHeight = 17.sp),
                    color = ReinColors.TextTertiary.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xF0081511), Color(0xFF081511))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                LiquidPrimaryButton(
                    text = when {
                        busy && registerMode -> "正在创建账号…"
                        busy -> "正在登录…"
                        registerMode -> "创建并登录"
                        else -> "登录"
                    },
                    enabled = !busy,
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = ReinColors.Mint.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = "安全连接 · 服务端只保存哈希后的密码",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                        color = ReinColors.TextTertiary.copy(alpha = 0.58f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthModeSelector(registerMode: Boolean, onModeChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.035f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AuthModeOption(
            label = "登录",
            selected = !registerMode,
            onClick = { onModeChange(false) },
            modifier = Modifier.weight(1f),
        )
        AuthModeOption(
            label = "注册",
            selected = registerMode,
            onClick = { onModeChange(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AuthModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = tween(220, easing = LiquidEase),
        label = "authModeBg",
    )
    val fg by animateColorAsState(
        if (selected) ReinColors.TextPrimary else ReinColors.TextTertiary,
        animationSpec = tween(220, easing = LiquidEase),
        label = "authModeFg",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(
                if (selected) Modifier.border(
                    1.dp,
                    ReinColors.Mint.copy(alpha = 0.18f),
                    RoundedCornerShape(12.dp),
                ) else Modifier
            )
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = fg,
        )
    }
}
