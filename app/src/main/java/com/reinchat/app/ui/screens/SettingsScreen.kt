package com.reinchat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.reinchat.app.BuildConfig
import com.reinchat.app.data.CloudAccount
import com.reinchat.app.data.ProviderConfig
import com.reinchat.app.ui.components.GlassDialog
import com.reinchat.app.ui.components.GlassIconButton
import com.reinchat.app.ui.components.GlassSurface
import com.reinchat.app.ui.components.GlassTextButton
import com.reinchat.app.ui.components.LiquidPrimaryButton
import com.reinchat.app.ui.components.Wordmark
import com.reinchat.app.ui.components.clickableNoRipple
import com.reinchat.app.ui.components.liquidGlass
import com.reinchat.app.ui.components.pressScale
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.LiquidEaseOutSoft
import com.reinchat.app.ui.theme.ReinColors

@Composable
fun SettingsScreen(
    providers: List<ProviderConfig>,
    activeProviderId: String?,
    account: CloudAccount?,
    cloudServerUrl: String,
    cloudSyncing: Boolean,
    cloudError: String?,
    authBusy: Boolean,
    authError: String?,
    focusProviderId: String?,
    onFocusConsumed: () -> Unit,
    onClose: () -> Unit,
    onSaveProvider: (ProviderConfig) -> Unit,
    onSetActiveProvider: (String) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onLogin: (String, String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onLogout: () -> Unit,
    onSyncCloud: () -> Unit,
    onClearAuthError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var authOpen by remember { mutableStateOf(false) }
    var logoutConfirm by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<ProviderConfig?>(null) }
    val overlayOpen = editorOpen || authOpen

    LaunchedEffect(account) {
        if (account != null) authOpen = false
    }

    fun openNew() {
        editingProvider = null
        editorOpen = true
    }

    fun openEdit(provider: ProviderConfig) {
        editingProvider = provider
        editorOpen = true
    }

    LaunchedEffect(focusProviderId) {
        if (focusProviderId != null) {
            val target = providers.firstOrNull { it.id == focusProviderId }
            if (target != null) openEdit(target) else openNew()
            onFocusConsumed()
        }
    }

    BackHandler(enabled = true) {
        when {
            authOpen -> authOpen = false
            editorOpen -> editorOpen = false
            else -> onClose()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFC060B09)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (overlayOpen) 0f else 1f }
        ) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ReinColors.Mint.copy(alpha = 0.17f),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.82f, -size.height * 0.04f),
                    radius = size.width * 0.9f,
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ReinColors.Cyan.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.28f),
                    radius = size.width * 0.8f,
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ReinColors.Violet.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.92f),
                    radius = size.width * 0.85f,
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (overlayOpen) 0f else 1f }
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onClose,
                    size = 40.dp,
                    iconSize = 18.dp,
                )
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp),
                        color = ReinColors.TextPrimary,
                    )
                    Text(
                        text = "服务商 · 本机数据",
                        style = MaterialTheme.typography.labelMedium,
                        color = ReinColors.TextTertiary,
                    )
                }
                GlassIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "添加服务商",
                    onClick = { openNew() },
                    size = 40.dp,
                    iconSize = 18.dp,
                    tint = ReinColors.Mint,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp),
            ) {
                Spacer(Modifier.height(24.dp))
                AccountSection(
                    account = account,
                    serverUrl = cloudServerUrl,
                    syncing = cloudSyncing,
                    syncError = cloudError,
                    onLoginClick = {
                        onClearAuthError()
                        authOpen = true
                    },
                    onLogoutClick = { logoutConfirm = true },
                    onSyncClick = onSyncCloud,
                )
                Spacer(Modifier.height(26.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "AI 服务商",
                        style = MaterialTheme.typography.titleLarge,
                        color = ReinColors.TextPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (providers.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${providers.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ReinColors.TextTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "直连 API",
                        style = MaterialTheme.typography.labelMedium,
                        color = ReinColors.TextTertiary.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(13.dp))

                Column(
                    modifier = Modifier.animateContentSize(
                        animationSpec = tween(300, easing = LiquidEaseOutSoft)
                    ),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (providers.isEmpty()) {
                        EmptyProvidersCard(onAdd = { openNew() })
                    } else {
                        providers.forEach { provider ->
                            ProviderCard(
                                provider = provider,
                                active = provider.id == (activeProviderId ?: providers.first().id),
                                onEdit = { openEdit(provider) },
                                onSetActive = { onSetActiveProvider(provider.id) },
                                onDelete = { deleteTarget = provider },
                            )
                        }
                        AddProviderButton(onClick = { openNew() })
                    }
                }

                Spacer(Modifier.height(28.dp))
                PrivacyCard()
                Spacer(Modifier.height(22.dp))
                AboutSection()
                Spacer(Modifier.height(30.dp))
            }
        }

        AnimatedVisibility(
            visible = editorOpen,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(4f),
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(400, easing = LiquidEase),
            ) + fadeIn(tween(180)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300, easing = LiquidEaseOutSoft),
            ) + fadeOut(tween(140)),
        ) {
            ProviderEditorScreen(
                initial = editingProvider,
                signedIn = account != null,
                onBack = { editorOpen = false },
                onSave = { provider ->
                    onSaveProvider(provider)
                    editorOpen = false
                },
            )
        }

        AnimatedVisibility(
            visible = authOpen,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(5f),
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(400, easing = LiquidEase),
            ) + fadeIn(tween(180)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300, easing = LiquidEaseOutSoft),
            ) + fadeOut(tween(140)),
        ) {
            AuthScreen(
                initialServerUrl = cloudServerUrl,
                account = account,
                busy = authBusy,
                error = authError,
                onBack = { authOpen = false },
                onClearError = onClearAuthError,
                onLogin = onLogin,
                onRegister = onRegister,
                onAuthenticated = { authOpen = false },
            )
        }
    }

    if (logoutConfirm) {
        GlassDialog(onDismiss = { logoutConfirm = false }) {
            Text(
                text = "退出登录？",
                style = MaterialTheme.typography.headlineSmall,
                color = ReinColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "退出后会从本机移除已同步到云端的会话和同步服务商配置，云端数据仍保留；下次登录会重新拉取。",
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
                    onClick = { logoutConfirm = false },
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(ReinColors.Danger.copy(alpha = 0.14f))
                        .border(1.dp, ReinColors.Danger.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
                        .clickableNoRipple {
                            logoutConfirm = false
                            onLogout()
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "退出登录",
                        style = MaterialTheme.typography.labelLarge,
                        color = ReinColors.Danger,
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        GlassDialog(onDismiss = { deleteTarget = null }) {
            Text(
                text = "删除服务商？",
                style = MaterialTheme.typography.headlineSmall,
                color = ReinColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "「${target.name}」的 Base URL、模型与 API Key 将从此设备移除。已经产生的对话不会受影响。",
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(ReinColors.Danger.copy(alpha = 0.14f))
                        .border(1.dp, ReinColors.Danger.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
                        .clickableNoRipple {
                            onDeleteProvider(target.id)
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
private fun AccountSection(
    account: CloudAccount?,
    serverUrl: String,
    syncing: Boolean,
    syncError: String?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSyncClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "账户",
                style = MaterialTheme.typography.titleLarge,
                color = ReinColors.TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (account != null) ReinColors.Mint.copy(alpha = 0.10f)
                        else Color.White.copy(alpha = 0.05f)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (account != null) "已登录" else "未登录",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                    color = if (account != null) ReinColors.Mint.copy(alpha = 0.85f)
                    else ReinColors.TextTertiary,
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(23.dp),
            topTint = Color.White.copy(alpha = 0.06f),
            bottomTint = Color.White.copy(alpha = 0.02f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            if (account == null) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(ReinColors.Mint.copy(alpha = 0.08f))
                            .border(1.dp, ReinColors.Mint.copy(alpha = 0.11f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = null,
                            tint = ReinColors.Mint.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "登录后开启多端同步",
                            style = MaterialTheme.typography.titleMedium,
                            color = ReinColors.TextPrimary,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "云端会话与未标记“仅本地”的服务商配置会跟随账号；未登录时只能保存在本机。",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 18.sp),
                            color = ReinColors.TextTertiary.copy(alpha = 0.85f),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                LiquidPrimaryButton(
                    text = "登录 / 注册",
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        ReinColors.Mint.copy(alpha = 0.24f),
                                        ReinColors.Cyan.copy(alpha = 0.10f),
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = account.username.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = ReinColors.Mint,
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.username,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = ReinColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = serverHost(account.serverUrl),
                            style = MaterialTheme.typography.labelMedium,
                            color = ReinColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 1.5.dp,
                            color = ReinColors.Mint.copy(alpha = 0.8f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        syncing -> "正在同步云端数据…"
                        syncError != null -> syncError
                        else -> "新会话默认开启云端保存 · 已同步服务商可在多端使用"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp),
                    color = if (syncError != null) ReinColors.Danger.copy(alpha = 0.85f)
                    else ReinColors.TextTertiary.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassTextButton(
                        text = if (syncing) "同步中…" else "立即同步",
                        tint = ReinColors.Mint.copy(alpha = if (syncing) 0.45f else 1f),
                        onClick = { if (!syncing) onSyncClick() },
                    )
                    Spacer(Modifier.weight(1f))
                    GlassTextButton(
                        text = "退出登录",
                        tint = ReinColors.TextTertiary,
                        onClick = onLogoutClick,
                    )
                }
            }
        }
    }
}

private fun serverHost(serverUrl: String): String =
    runCatching { java.net.URI(serverUrl).host }.getOrNull()?.ifBlank { serverUrl } ?: serverUrl

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    active: Boolean,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(23.dp)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.99f)
            .liquidGlass(
                shape = shape,
                topTint = if (active) ReinColors.Mint.copy(alpha = 0.075f)
                else Color.White.copy(alpha = 0.06f),
                bottomTint = if (active) ReinColors.Cyan.copy(alpha = 0.025f)
                else Color.White.copy(alpha = 0.02f),
                borderAlpha = if (active) 1.35f else 0.9f,
                elevation = if (active) 3.dp else 0.dp,
                glowColor = if (active) ReinColors.Mint.copy(alpha = 0.13f) else null,
            )
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onEdit)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (active) {
                            Brush.linearGradient(
                                listOf(ReinColors.Mint.copy(alpha = 0.20f), ReinColors.Cyan.copy(alpha = 0.10f))
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.03f))
                            )
                        }
                    )
                    .border(
                        1.dp,
                        if (active) ReinColors.Mint.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                        RoundedCornerShape(11.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = provider.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (active) ReinColors.Mint else ReinColors.TextSecondary,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ReinColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (provider.protocol == com.reinchat.app.data.ApiProtocol.RESPONSES) {
                                    ReinColors.Violet.copy(alpha = 0.11f)
                                } else {
                                    ReinColors.Cyan.copy(alpha = 0.09f)
                                }
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = provider.endpointLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                            color = if (provider.protocol == com.reinchat.app.data.ApiProtocol.RESPONSES)
                                ReinColors.Violet.copy(alpha = 0.9f)
                            else ReinColors.Cyan.copy(alpha = 0.9f),
                        )
                    }
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "当前使用",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                            color = ReinColors.Mint.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickableNoRipple(onSetActive),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(21.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) ReinColors.Mint.copy(alpha = 0.16f) else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (active) ReinColors.Mint.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.16f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "当前服务商",
                            tint = ReinColors.Mint,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickableNoRipple(onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "删除服务商",
                    tint = ReinColors.TextTertiary.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(13.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.07f), Color.Transparent)
                    )
                )
        )
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    append(provider.activeModel.ifBlank { "未填写模型" })
                    if (provider.modelOptions.size > 1) {
                        append("  +${provider.modelOptions.size - 1}")
                    }
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = ReinColors.TextSecondary.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(9.dp))
            Icon(
                imageVector = Icons.Rounded.Cloud,
                contentDescription = null,
                tint = if (provider.localOnly) ReinColors.TextTertiary.copy(alpha = 0.45f)
                else ReinColors.Mint.copy(alpha = 0.65f),
                modifier = Modifier.size(10.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (provider.localOnly) "仅本地" else "云端",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                color = if (provider.localOnly) ReinColors.TextTertiary.copy(alpha = 0.6f)
                else ReinColors.Mint.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(9.dp))
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = ReinColors.TextTertiary.copy(alpha = 0.55f),
                modifier = Modifier.size(10.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (provider.apiKey.isBlank()) "未设置密钥" else "密钥已加密",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                color = if (provider.apiKey.isBlank()) ReinColors.Warm.copy(alpha = 0.65f)
                else ReinColors.TextTertiary.copy(alpha = 0.65f),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = provider.baseUrl.ifBlank { "尚未填写 Base URL" },
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            color = ReinColors.TextTertiary.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddProviderButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.99f)
            .border(1.dp, Color.White.copy(alpha = 0.075f), shape)
            .background(Color.White.copy(alpha = 0.022f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = ReinColors.Mint.copy(alpha = 0.85f),
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "添加服务商",
            style = MaterialTheme.typography.labelLarge,
            color = ReinColors.TextSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Responses / Chat Completions",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
            color = ReinColors.TextTertiary.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun EmptyProvidersCard(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                topTint = Color.White.copy(alpha = 0.062f),
                bottomTint = Color.White.copy(alpha = 0.02f),
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "还没有服务商",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ReinColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "连接 OpenAI、DeepSeek、OpenRouter，或任何兼容的自建接口。",
            style = MaterialTheme.typography.bodyMedium,
            color = ReinColors.TextSecondary.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(15.dp))
        LiquidPrimaryButton(
            text = "添加第一个服务商",
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PrivacyCard() {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        topTint = Color.White.copy(alpha = 0.045f),
        bottomTint = Color.White.copy(alpha = 0.018f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(17.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(33.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(ReinColors.Mint.copy(alpha = 0.075f))
                    .border(1.dp, ReinColors.Mint.copy(alpha = 0.1f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = ReinColors.Mint.copy(alpha = 0.8f),
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "密钥与对话数据的去向",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = ReinColors.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "API Key 默认加密保存在本机。关闭某个服务商的“仅本地”后，它的完整配置（含 API Key）会加密同步到你的账号服务器；标记为云端的会话也会存储在服务器并同步到同账号设备。其余数据只留在本机。",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 19.sp),
                    color = ReinColors.TextTertiary.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Wordmark(compact = true)
        Spacer(Modifier.height(7.dp))
        Text(
            text = "ReinChat ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = ReinColors.TextTertiary.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "为 HarmonyOS 7 · 卓易通 倾心打造",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
            color = ReinColors.TextTertiary.copy(alpha = 0.45f),
        )
    }
}
