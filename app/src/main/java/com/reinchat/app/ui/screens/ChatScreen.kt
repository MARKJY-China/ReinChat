package com.reinchat.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LightMode
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.reinchat.app.data.ChatImage
import com.reinchat.app.data.ChatMessage
import com.reinchat.app.data.ProviderConfig
import com.reinchat.app.ui.ChromeMetrics
import com.reinchat.app.ui.components.Composer
import com.reinchat.app.ui.components.EmptyChat
import com.reinchat.app.ui.components.GlassDialog
import com.reinchat.app.ui.components.GlassIconButton
import com.reinchat.app.ui.components.GlassTextButton
import com.reinchat.app.ui.components.MessageItem
import com.reinchat.app.ui.components.clickableNoRipple
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.LocalReinThemeControl
import com.reinchat.app.ui.theme.ReinColors
import com.reinchat.app.vm.ChatUiState
import com.reinchat.app.vm.StreamPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    onAttachPdf: (android.net.Uri) -> Unit,
    onRemovePdf: () -> Unit,
    onPasteClipboard: (ClipData?) -> Boolean,
    onPasteUris: (List<android.net.Uri>) -> Boolean,
    onClearPdfError: () -> Unit,
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
    var followLatest by remember { mutableStateOf(true) }
    val themeControl = LocalReinThemeControl.current
    val composerHeight = with(LocalDensity.current) {
        ChromeMetrics.composerHeightPx.toDp()
    } + 12.dp

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

    val context = LocalContext.current
    val platformClipboard = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onAttachPdf) }

    // Compose exposes no file-URI paste callback, so read the platform clip
    // directly: first the item URIs, then any content:// or file:// URLs that
    // were copied as plain text.
    fun pasteAttachments() {
        val clip = platformClipboard?.primaryClip
        if (clip != null && onPasteClipboard(clip)) return
        val text = clip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
        val uris = Regex("(?:content|file)://[^\\s\"']+")
            .findAll(text)
            .map { android.net.Uri.parse(it.value) }
            .toList()
        if (uris.isNotEmpty() && onPasteUris(uris)) return
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "剪贴板中没有可添加的图片或 PDF",
                duration = SnackbarDuration.Short,
            )
        }
    }

    LaunchedEffect(state.activeConversationId) {
        followLatest = true
    }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) followLatest = false
        }
    }
    LaunchedEffect(atBottom, listState.isScrollInProgress) {
        if (atBottom && !listState.isScrollInProgress) followLatest = true
    }
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
                listState.animateScrollToItem(messages.lastIndex, scrollOffset = 0)
            }
        }
    }

    val imeVisible = WindowInsets.isImeVisible
    val bottomInset = if (imeVisible) WindowInsets.ime else WindowInsets.navigationBars
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = statusBarTop + 57.dp

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
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = headerHeight + 4.dp, bottom = composerHeight + 8.dp),
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(
                            top = headerHeight + 2.dp,
                            bottom = composerHeight + 16.dp,
                        ),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        itemsIndexed(
                            items = messages,
                            key = { _, message -> message.id },
                        ) { index, message ->
                            MessageItem(
                                message = message,
                                animateIn = message.createdAt >= screenOpenedAt - 100,
                                isStreaming = message.id == streamingId,
                                streamPhase = if (message.id == streamingId) {
                                    state.streamPhase
                                } else {
                                    StreamPhase.IDLE
                                },
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
                                showDivider = index < messages.lastIndex,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = !atBottom && messages.isNotEmpty(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 2.dp, bottom = composerHeight + 10.dp),
                        enter = fadeIn(tween(150)) + scaleIn(tween(190, easing = LiquidEase), initialScale = 0.85f),
                        exit = fadeOut(tween(110)) + scaleOut(tween(140), targetScale = 0.85f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ReinColors.Surface)
                                .border(1.dp, ReinColors.Hairline, CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    followLatest = true
                                    scope.launch {
                                        listState.animateScrollToItem(
                                            index = messages.lastIndex,
                                            scrollOffset = 100_000,
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "回到最新消息",
                                tint = ReinColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        if (renderChrome) {
            ChatHeader(
                title = state.activeConversation?.title ?: "ReinChat",
                subtitle = if (provider != null) {
                    buildString {
                        append(provider.name)
                        append(" · ")
                        append(provider.activeModel.ifBlank { "未设置模型" })
                        if (state.activeConversation != null) {
                            append(if (activeCloud) " · 云端" else " · 本地")
                        }
                    }
                } else {
                    "尚未连接服务商"
                },
                cloudActive = activeCloud,
                darkTheme = themeControl?.darkTheme == true,
                onToggleTheme = themeControl?.onToggle,
                onOpenDrawer = onOpenDrawer,
                onNewChat = onNewChat,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Composer(
                    draft = state.draft,
                    pendingAttachments = state.pendingAttachments,
                    isStreaming = state.isStreaming,
                    importingImage = state.importingImage,
                    pdfBusy = state.pdfBusy,
                    pdfError = state.pdfError,
                    editing = state.editingMessageId != null,
                    providerLabel = provider?.let { it.activeModel.ifBlank { it.name } },
                    webSearch = state.webSearch,
                    searchAvailable = (state.account?.level ?: 1) >= 2,
                    relayMode = (state.account?.level ?: 1) >= 2,
                    onToggleWebSearch = onToggleWebSearch,
                    canSend = state.canSend && !state.importingImage && !state.pdfBusy,
                    onDraftChange = onDraftChange,
                    onAddImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onAttachPdf = {
                        onClearPdfError()
                        pdfPicker.launch(arrayOf("application/pdf"))
                    },
                    onPaste = { pasteAttachments() },
                    onRemovePdf = onRemovePdf,
                    onClearPdfError = onClearPdfError,
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
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .onSizeChanged { ChromeMetrics.composerHeightPx = it.height },
                )
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
    cloudActive: Boolean,
    darkTheme: Boolean,
    onToggleTheme: (() -> Unit)?,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                icon = Icons.Rounded.Menu,
                contentDescription = "打开会话面板",
                onClick = onOpenDrawer,
                size = 36.dp,
                iconSize = 19.dp,
            )
            Spacer(Modifier.width(2.dp))
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (fadeIn(tween(180, easing = LiquidEase)) +
                            slideInVertically(tween(220, easing = LiquidEase)) { it / 3 })
                            .togetherWith(
                                fadeOut(tween(110)) +
                                    slideOutVertically(tween(150)) { -it / 4 }
                            )
                    },
                    label = "headerTitle",
                ) { target ->
                    Text(
                        text = target,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = ReinColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                    color = ReinColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (cloudActive) ReinColors.AccentSoft else ReinColors.DeepElevated)
                    .semantics {
                        contentDescription = if (cloudActive) "云端同步已开启" else "仅保存在本机"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (cloudActive) ReinColors.Mint else ReinColors.TextTertiary)
                )
            }
            if (onToggleTheme != null) {
                GlassIconButton(
                    icon = if (darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = if (darkTheme) "切换到浅色主题" else "切换到暗色主题",
                    onClick = onToggleTheme,
                    size = 36.dp,
                    iconSize = 17.dp,
                )
            }
            GlassIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "新建对话",
                onClick = onNewChat,
                size = 36.dp,
                iconSize = 18.dp,
            )
            GlassIconButton(
                icon = Icons.Rounded.Tune,
                contentDescription = "设置",
                onClick = onOpenSettings,
                size = 36.dp,
                iconSize = 17.dp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ReinColors.Hairline)
        )
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
        Text(
            text = "服务商与模型",
            style = MaterialTheme.typography.headlineSmall,
            color = ReinColors.TextPrimary,
        )
        Text(
            text = "切换后，下一条消息将使用新的模型",
            style = MaterialTheme.typography.labelMedium,
            color = ReinColors.TextTertiary,
        )
        if (activeProvider != null) {
            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "思考强度",
                    style = MaterialTheme.typography.labelLarge,
                    color = ReinColors.TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "低 · 高 · 最高",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                    color = ReinColors.TextTertiary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (selected) ReinColors.AccentSoft else ReinColors.DeepElevated)
                            .border(
                                1.dp,
                                if (selected) ReinColors.Mint.copy(alpha = 0.35f) else ReinColors.Hairline,
                                RoundedCornerShape(7.dp),
                            )
                            .clickableNoRipple {
                                onSelectReasoningEffort(activeProvider.id, value)
                            }
                            .padding(vertical = 6.dp),
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
            Spacer(Modifier.height(14.dp))
            Text(
                text = "模型",
                style = MaterialTheme.typography.labelLarge,
                color = ReinColors.TextSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 168.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                activeProvider.modelOptions.forEach { model ->
                    val selected = model == activeModel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (selected) ReinColors.AccentSoft else Color.Transparent)
                            .clickableNoRipple {
                                onSelectModel(activeProvider.id, model)
                            }
                            .padding(horizontal = 9.dp, vertical = 8.dp),
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
            Spacer(Modifier.height(14.dp))
        } else {
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = "服务商",
            style = MaterialTheme.typography.labelLarge,
            color = ReinColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            providers.forEach { provider ->
                val selected = provider.id == activeProviderId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) ReinColors.AccentSoft else Color.Transparent)
                        .clickableNoRipple { onSelect(provider.id) }
                        .padding(horizontal = 9.dp, vertical = 9.dp),
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
                        Text(
                            text = "${provider.activeModel}  ·  ${provider.endpointLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ReinColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "当前使用",
                            tint = ReinColors.Mint,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
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
                .background(Color(0xFF080806))
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
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭图片",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = if (scale > 1f) "双指缩放 · 拖动查看" else "双指缩放",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 46.dp),
            )
        }
    }
}
