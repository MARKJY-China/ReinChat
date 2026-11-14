package com.reinchat.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.reinchat.app.data.ChatImage
import com.reinchat.app.ui.ChromeMetrics
import com.reinchat.app.data.ChatMessage
import com.reinchat.app.data.MessageRole
import com.reinchat.app.data.ProviderConfig
import com.reinchat.app.ui.components.Composer
import com.reinchat.app.ui.components.EmptyChat
import com.reinchat.app.ui.components.GlassDialog
import com.reinchat.app.ui.components.GlassIconButton
import com.reinchat.app.ui.components.GlassTextButton
import com.reinchat.app.ui.components.LiquidOrb
import com.reinchat.app.ui.components.MessageItem
import com.reinchat.app.ui.components.clickableNoRipple
import com.reinchat.app.ui.components.liquidGlass
import com.reinchat.app.vm.ChatUiState
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.LiquidEaseOutSoft
import com.reinchat.app.ui.theme.ReinColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    providers: List<ProviderConfig>,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onDraftChange: (String) -> Unit,
    onAddImages: (List<android.net.Uri>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCancelEdit: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onSelectReasoningEffort: (String, String) -> Unit,
    onToggleWebSearch: () -> Unit,
    onRetryMessage: (String) -> Unit,
    onEditMessage: (String) -> Unit,
    onRewind: (String) -> Boolean,
    onUndoRewind: () -> Boolean,
    onSetNewChatCloud: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    renderContent: Boolean = true,
    renderChrome: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val messages = state.activeConversation?.messages.orEmpty()
    val streamingId = state.streaming?.messageId
    val screenOpenedAt = remember { System.currentTimeMillis() }
    val provider = state.activeProvider
    var providerPickerOpen by remember { mutableStateOf(false) }
    var viewerImage by remember { mutableStateOf<ChatImage?>(null) }
    var openActionsId by remember { mutableStateOf<String?>(null) }
    val composerHeight = with(LocalDensity.current) {
        ChromeMetrics.composerHeightPx.toDp()
    } + 12.dp
    var followLatest by remember { mutableStateOf(true) }
    val topFadeDistancePx = with(LocalDensity.current) { 72.dp.toPx() }
    // The header scrim is invisible at the very top. It appears only once the
    // message list has actually moved under the header.
    val topScrimAlpha by remember(listState, topFadeDistancePx) {
        derivedStateOf {
            if (!listState.canScrollBackward) {
                0f
            } else if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / topFadeDistancePx).coerceIn(0f, 1f)
            }
        }
    }

    val lastMessageIndex = messages.lastIndex
    val atBottom by remember(listState, lastMessageIndex) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index == lastMessageIndex &&
                last.offset + last.size <= layout.viewportEndOffset + 6
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris -> if (uris.isNotEmpty()) onAddImages(uris) }

    LaunchedEffect(state.activeConversationId) {
        followLatest = true
    }
    // A real drag detaches from follow mode. Scrolling back to the bottom
    // re-enters it.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) followLatest = false
        }
    }
    LaunchedEffect(atBottom, listState.isScrollInProgress) {
        if (atBottom && !listState.isScrollInProgress) followLatest = true
    }
    // While following, keep the viewport pinned to the newest text. The loop
    // stops as soon as a drag detaches follow mode or generation finishes.
    LaunchedEffect(followLatest, state.isStreaming, messages.lastOrNull()?.id) {
        if (!followLatest) return@LaunchedEffect
        while (isActive && state.isStreaming && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex, scrollOffset = 100_000)
            delay(32)
        }
    }

    LaunchedEffect(state.activeConversationId, messages.firstOrNull()?.id) {
        openActionsId = null
        if (messages.isNotEmpty()) {
            val activeStream = state.streaming?.conversationId == state.activeConversationId
            // Opening a finished conversation goes to the newest message.
            // Opening/attaching a live stream anchors the *start* of the
            // growing bubble, so new lines extend below instead of moving
            // the reading viewport.
            listState.animateScrollToItem(
                index = messages.lastIndex,
                scrollOffset = if (activeStream) 0 else 100_000,
            )
        }
    }
    LaunchedEffect(streamingId) {
        if (streamingId != null) {
            openActionsId = null
            if (!followLatest && messages.lastOrNull()?.id == streamingId) {
                // Reading an older part: pin the start of the new reply
                // instead of following its tail.
                listState.animateScrollToItem(messages.lastIndex, scrollOffset = 0)
            }
        }
    }

    val imeVisible = WindowInsets.isImeVisible
    val bottomInset = if (imeVisible) WindowInsets.ime else WindowInsets.navigationBars
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            )
            .windowInsetsPadding(bottomInset.only(WindowInsetsSides.Bottom)),
    ) {
        val activeCloud = state.activeConversation?.cloud == true

        if (renderContent) {
        Box(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 100.dp + statusBarTop, bottom = composerHeight + 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyChat(
                    providerConfigured = provider != null,
                    providerLabel = provider?.let { "${it.name} · ${it.activeModel}" },
                    cloudAvailable = state.isLoggedIn,
                    cloudEnabled = state.isLoggedIn && state.newChatCloud,
                    onCloudChange = onSetNewChatCloud,
                    onLoginClick = onOpenSettings,
                    onConfigure = onOpenSettings,
                    onSuggestion = { suggestion ->
                        onDraftChange(suggestion)
                        scope.launch {
                            delay(70)
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(
                        top = 100.dp + statusBarTop,
                        bottom = composerHeight + 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(27.dp),
                ) {
                    items(
                        items = messages,
                        key = { it.id },
                    ) { message ->
                        MessageItem(
                            message = message,
                            animateIn = message.createdAt >= screenOpenedAt - 100,
                            isStreaming = message.id == streamingId,
                            canRewind = !state.isStreaming && message.id != messages.lastOrNull()?.id,
                            actionsOpen = openActionsId == message.id,
                            onToggleActions = {
                                if (!state.isStreaming) {
                                    openActionsId = if (openActionsId == message.id) null else message.id
                                }
                            },
                            onCloseActions = { openActionsId = null },
                            onCopy = { copied ->
                                if (copied.content.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(copied.content))
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "已复制",
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                }
                            },
                            onRetry = { onRetryMessage(it.id) },
                            onEdit = { onEditMessage(it.id) },
                            onRewind = { target ->
                                if (onRewind(target.id)) {
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "已回退到此处",
                                            actionLabel = "撤销",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onUndoRewind()
                                        }
                                    }
                                }
                            },
                            onOpenImage = { viewerImage = it },
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = !atBottom && messages.isNotEmpty(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = composerHeight + 10.dp),
                    enter = fadeIn(tween(180)) + scaleIn(tween(240, easing = LiquidEase), initialScale = 0.8f),
                    exit = fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.8f),
                ) {
                    GlassIconButton(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "回到最新消息",
                        onClick = {
                            followLatest = true
                            scope.launch {
                                listState.animateScrollToItem(
                                    index = messages.lastIndex,
                                    scrollOffset = 100_000,
                                )
                            }
                        },
                        size = 38.dp,
                        iconSize = 19.dp,
                    )
                }
            }
        }
        }
        }

        if (renderChrome) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusBarTop + 96.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF050C09).copy(alpha = 0.902f * topScrimAlpha),
                            0.58f to Color(0xFF050C09).copy(alpha = 0.902f * topScrimAlpha),
                            0.82f to Color(0xFF050C09).copy(alpha = 0.651f * topScrimAlpha),
                            1f to Color.Transparent,
                        )
                    )
                )
        )
        ChatHeader(
            title = state.activeConversation?.title ?: "ReinChat",
            subtitle = if (provider != null) {
                buildString {
                    append(provider.name)
                    append("  ·  ")
                    append(provider.activeModel.ifBlank { "未设置模型" })
                    if (state.activeConversation != null) {
                        append(if (activeCloud) "  ·  云端" else "  ·  仅本地")
                    }
                }
            } else {
                "驭聊 · 一个安静的 AI 对话空间"
            },
            onOpenDrawer = onOpenDrawer,
            onNewChat = onNewChat,
            onOpenSettings = onOpenSettings,
            topInset = statusBarTop,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        }

        if (renderChrome) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(composerHeight + 64.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.42f to Color(0x80050C09),
                            0.58f to Color(0xE6050C09),
                            1f to Color(0xE6050C09),
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(29.dp))
                    .background(Color(0xE6050C09)),
            ) {
                Composer(
                    draft = state.draft,
                attachments = state.attachments,
                isStreaming = state.isStreaming,
                importingImage = state.importingImage,
                editing = state.editingMessageId != null,
                providerLabel = provider?.let { it.activeModel.ifBlank { it.name } },
                webSearch = state.webSearch,
                searchAvailable = (state.account?.level ?: 1) >= 2,
                relayMode = (state.account?.level ?: 1) >= 2,
                onToggleWebSearch = onToggleWebSearch,
                canSend = state.canSend && !state.importingImage,
                onDraftChange = onDraftChange,
                onAddImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveAttachment = onRemoveAttachment,
                onSend = {
                    followLatest = true
                    onSend()
                },
                onStop = onStop,
                onCancelEdit = onCancelEdit,
                onProviderClick = {
                    if (providers.isEmpty()) onOpenSettings() else providerPickerOpen = true
                },
                focusRequester = focusRequester,
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .onSizeChanged { ChromeMetrics.composerHeightPx = it.height },
                )
            }
        }
        }
    }

    if (providerPickerOpen && renderChrome) {
        ProviderPickerDialog(
            providers = providers,
            activeProviderId = provider?.id,
            activeModel = provider?.activeModel,
            onDismiss = { providerPickerOpen = false },
            onSelect = {
                onSelectProvider(it)
                providerPickerOpen = false
            },
            onSelectModel = { providerId, model ->
                onSelectModel(providerId, model)
                providerPickerOpen = false
            },
            onSelectReasoningEffort = { providerId, effort ->
                onSelectReasoningEffort(providerId, effort)
            },
            onManage = {
                providerPickerOpen = false
                onOpenSettings()
            },
        )
    }

    viewerImage?.let { image ->
        ImageViewerDialog(image = image, onDismiss = { viewerImage = null })
    }
}

@Composable
private fun ChatHeader(
    title: String,
    subtitle: String,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = topInset + 6.dp, bottom = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                icon = Icons.Rounded.Menu,
                contentDescription = "打开会话面板",
                onClick = onOpenDrawer,
                size = 40.dp,
                iconSize = 19.dp,
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = title to subtitle,
                    transitionSpec = {
                        (fadeIn(tween(240, easing = LiquidEase)) +
                            slideInVertically(tween(300, easing = LiquidEase)) { it / 3 })
                            .togetherWith(
                                fadeOut(tween(120)) +
                                    slideOutVertically(tween(180)) { -it / 4 }
                            )
                    },
                    label = "headerTitle",
                ) { target ->
                    Column {
                        Text(
                            text = target.first,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = ReinColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = target.second,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                            color = ReinColors.TextTertiary.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            GlassIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "新建对话",
                onClick = onNewChat,
                size = 40.dp,
                iconSize = 19.dp,
            )
            Spacer(Modifier.width(7.dp))
            GlassIconButton(
                icon = Icons.Rounded.Tune,
                contentDescription = "设置",
                onClick = onOpenSettings,
                size = 40.dp,
                iconSize = 18.dp,
            )
        }
    }
}

@Composable
private fun ProviderPickerDialog(
    providers: List<ProviderConfig>,
    activeProviderId: String?,
    activeModel: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onSelectReasoningEffort: (String, String) -> Unit,
    onManage: () -> Unit,
) {
    val activeProvider = providers.firstOrNull { it.id == activeProviderId }
    GlassDialog(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiquidOrb(size = 30.dp, letter = true)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "选择服务商与模型",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ReinColors.TextPrimary,
                )
                Text(
                    text = "切换后，下一条消息将使用新的模型",
                    style = MaterialTheme.typography.labelMedium,
                    color = ReinColors.TextTertiary,
                )
            }
        }
        if (activeProvider != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "思考强度",
                    style = MaterialTheme.typography.labelLarge,
                    color = ReinColors.TextTertiary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "低=low · 高=high · 最高=max",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.5.sp),
                    color = ReinColors.TextTertiary.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                listOf(
                    "" to "自动",
                    "low" to "低",
                    "high" to "高",
                    "max" to "最高",
                ).forEach { (value, label) ->
                    val selected = activeProvider.reasoningEffort == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (selected) ReinColors.Mint.copy(alpha = 0.12f)
                                else Color.White.copy(alpha = 0.025f)
                            )
                            .border(
                                1.dp,
                                if (selected) ReinColors.Mint.copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(9.dp),
                            )
                            .clickableNoRipple {
                                onSelectReasoningEffort(activeProvider.id, value)
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                            color = if (selected) ReinColors.Mint else ReinColors.TextSecondary,
                        )
                    }
                }
            }
        }
        if (activeProvider != null && activeProvider.modelOptions.size > 1) {
            Spacer(Modifier.height(15.dp))
            Text(
                text = "当前服务商的模型",
                style = MaterialTheme.typography.labelLarge,
                color = ReinColors.TextTertiary,
            )
            Spacer(Modifier.height(7.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 176.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                activeProvider.modelOptions.forEach { model ->
                    val selected = model == activeModel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (selected) ReinColors.Mint.copy(alpha = 0.09f)
                                else Color.White.copy(alpha = 0.025f)
                            )
                            .clickableNoRipple {
                                onSelectModel(activeProvider.id, model)
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = model,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) ReinColors.Mint else ReinColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "当前模型",
                                tint = ReinColors.Mint,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(15.dp))
            Text(
                text = "服务商",
                style = MaterialTheme.typography.labelLarge,
                color = ReinColors.TextTertiary,
            )
            Spacer(Modifier.height(7.dp))
        } else {
            Spacer(Modifier.height(18.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            providers.forEach { provider ->
                val selected = provider.id == activeProviderId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) ReinColors.Mint.copy(alpha = 0.075f)
                            else Color.White.copy(alpha = 0.025f)
                        )
                        .then(
                            if (selected) Modifier.border(
                                1.dp,
                                ReinColors.Mint.copy(alpha = 0.16f),
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
                        .clickableNoRipple { onSelect(provider.id) }
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = ReinColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${provider.activeModel}  ·  ${provider.endpointLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ReinColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(ReinColors.Mint.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "当前使用",
                                tint = ReinColors.Mint,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            GlassTextButton(text = "管理服务商", onClick = onManage)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ImageViewerDialog(image: ChatImage, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF030706))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                },
        ) {
            AsyncImage(
                model = image.path,
                contentDescription = "查看图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                GlassIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "关闭图片",
                    onClick = onDismiss,
                    size = 42.dp,
                    iconSize = 18.dp,
                    background = true,
                )
            }
            Text(
                text = if (scale > 1f) "双指缩放 · 拖动查看" else "双指缩放",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.42f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 46.dp),
            )
        }
    }
}
