package com.reinchat.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.reinchat.app.ui.theme.LocalReinThemeControl
import com.reinchat.app.ui.theme.ReinColors

/**
 * Minimal full-screen sign-in, matching the web client's editorial login:
 * wordmark, oversized heading, hairline fields, one inked primary action.
 */
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

    val themeControl = LocalReinThemeControl.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    size = 38.dp,
                    iconSize = 17.dp,
                )
                Spacer(Modifier.width(6.dp))
                Wordmark(compact = true)
                Spacer(Modifier.weight(1f))
                themeControl?.let { control ->
                    GlassIconButton(
                        icon = if (control.darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = if (control.darkTheme) "切换到浅色主题" else "切换到暗色主题",
                        onClick = control.onToggle,
                        size = 38.dp,
                        iconSize = 17.dp,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp),
            ) {
                Spacer(Modifier.height(22.dp))
                Column(modifier = Modifier.widthIn(max = 520.dp)) {
                    Text(
                        text = "A QUIET PLACE TO THINK",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 9.5.sp,
                            letterSpacing = 2.4.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = ReinColors.TextTertiary,
                    )
                    Spacer(Modifier.height(13.dp))
                    Text(
                        text = "安静地，\n和 AI 对话。",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 40.sp,
                            lineHeight = 45.sp,
                            letterSpacing = (-1.3).sp,
                        ),
                        color = ReinColors.TextPrimary,
                    )
                    Spacer(Modifier.height(13.dp))
                    Text(
                        text = "一个克制的对话空间。支持云端同步与自己的模型。",
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = ReinColors.TextSecondary,
                    )

                    Spacer(Modifier.height(28.dp))

                    GlassInputField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = "服务器",
                        placeholder = DEFAULT_CLOUD_SERVER,
                        supportingText = "填 reinchat.markjy.com，或完整的 /api/v1 地址",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(Modifier.height(13.dp))

                    GlassInputField(
                        value = username,
                        onValueChange = {
                            username = it
                            onClearError()
                        },
                        label = "用户名",
                        placeholder = "3–32 位，支持中英文与 _ - .",
                        supportingText = if (registerMode) "账号名全站唯一，不区分大小写" else null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(Modifier.height(13.dp))

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
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
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
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(110)) },
                        label = "authError",
                    ) { message ->
                        if (message != null) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                ),
                                color = ReinColors.Danger,
                                modifier = Modifier.padding(top = 12.dp, start = 1.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    LiquidPrimaryButton(
                        text = when {
                            busy && registerMode -> "正在创建账号…"
                            busy -> "连接中…"
                            registerMode -> "创建账号"
                            else -> "继续"
                        },
                        enabled = !busy,
                        trailingIcon = if (!busy) Icons.AutoMirrored.Rounded.ArrowForward else null,
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (registerMode) "已有账号？登录" else "没有账号？注册",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                            color = ReinColors.TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    registerMode = !registerMode
                                    onClearError()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "登录时使用的账号密码只发送到你填写的服务器。未标记“仅本地”的服务商配置会同步到该账号；所有请求都经 HTTPS（或你填写的明文地址）传输。",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 10.sp,
                            lineHeight = 16.sp,
                        ),
                        color = ReinColors.TextTertiary,
                    )
                    Spacer(Modifier.height(28.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ReinChat",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                            color = ReinColors.TextTertiary.copy(alpha = 0.8f),
                        )
                        Text(
                            text = "服务端中转 · 不保存明文密钥",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                            color = ReinColors.TextTertiary.copy(alpha = 0.8f),
                        )
                    }
                    Spacer(Modifier.height(26.dp))
                }
            }
        }
    }
}
