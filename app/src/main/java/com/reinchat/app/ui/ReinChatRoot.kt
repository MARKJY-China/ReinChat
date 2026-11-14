package com.reinchat.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reinchat.app.BuildConfig
import com.reinchat.app.ui.components.AuroraBackground
import com.reinchat.app.ui.components.ReinSnackbarHost
import com.reinchat.app.ui.components.UpdateDialog
import com.reinchat.app.ui.screens.ChatScreen
import com.reinchat.app.ui.screens.SessionDrawerOverlay
import com.reinchat.app.ui.screens.SettingsScreen
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.ReinColors
import com.reinchat.app.update.UpdateDownloadState
import com.reinchat.app.update.installDownloadedApk
import com.reinchat.app.vm.ChatEvent
import com.reinchat.app.vm.ChatViewModel

enum class RootMode { FUSED, CONTENT, CHROME }

@Composable
fun ReinChatRoot(
    viewModel: ChatViewModel = viewModel(),
    mode: RootMode = RootMode.FUSED,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var focusProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val overlayOpen = drawerOpen || settingsOpen
    val blurRadius by animateDpAsState(
        targetValue = if (overlayOpen && mode == RootMode.FUSED) 14.dp else 0.dp,
        animationSpec = tween(380, easing = LiquidEase),
        label = "overlayBlur",
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.persistNow()
                Lifecycle.Event.ON_START -> viewModel.onAppForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdate()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.OpenSettings -> {
                    drawerOpen = false
                    focusProviderId = event.focusProviderId
                    settingsOpen = true
                }
                is ChatEvent.Notice -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (mode == RootMode.CHROME) Color.Transparent else ReinColors.Deep),
    ) {
        if (mode != RootMode.CHROME) AuroraBackground()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurRadius > 0.1.dp) Modifier.blur(blurRadius) else Modifier)
        ) {
            ChatScreen(
                state = state,
                providers = state.settings.providers,
                snackbarHostState = snackbarHostState,
                onOpenDrawer = { drawerOpen = true },
                onNewChat = { viewModel.newConversation() },
                onOpenSettings = {
                    focusProviderId = null
                    settingsOpen = true
                },
                onDraftChange = viewModel::setDraft,
                onAddImages = viewModel::addImages,
                onRemoveAttachment = viewModel::removeAttachment,
                onSend = viewModel::send,
                onStop = viewModel::stopStreaming,
                onCancelEdit = viewModel::cancelEdit,
                onSelectProvider = viewModel::setActiveProvider,
                onSelectModel = viewModel::setActiveModel,
                onSelectReasoningEffort = viewModel::setReasoningEffort,
                onToggleWebSearch = viewModel::toggleWebSearch,
                onRetryMessage = viewModel::retryResponse,
                onEditMessage = viewModel::beginEdit,
                onRewind = viewModel::rewindTo,
                onUndoRewind = viewModel::undoRewind,
                onSetNewChatCloud = viewModel::setNewChatCloud,
                renderContent = mode != RootMode.CHROME,
                renderChrome = mode != RootMode.CONTENT,
            )
        }

        if (mode != RootMode.CONTENT) {
        SessionDrawerOverlay(
            visible = drawerOpen,
            conversations = state.conversations,
            activeConversationId = state.activeConversationId,
            onClose = { drawerOpen = false },
            onNew = viewModel::newConversation,
            onSelect = viewModel::selectConversation,
            onRename = viewModel::renameConversation,
            onDelete = viewModel::deleteConversation,
            onOpenSettings = {
                focusProviderId = null
                settingsOpen = true
            },
        )

        AnimatedVisibility(
            visible = settingsOpen,
            modifier = Modifier.fillMaxSize().zIndex(3f),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(460, easing = LiquidEase),
            ) + fadeIn(tween(260)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(320, easing = LiquidEase),
            ) + fadeOut(tween(180)),
        ) {
            SettingsScreen(
                providers = state.settings.providers,
                activeProviderId = state.settings.activeProviderId,
                account = state.account,
                cloudServerUrl = state.settings.cloudServerUrl,
                cloudSyncing = state.cloudSyncing,
                cloudError = state.cloudError,
                authBusy = state.authBusy,
                authError = state.authError,
                focusProviderId = focusProviderId,
                onFocusConsumed = { focusProviderId = null },
                onClose = { settingsOpen = false },
                onSaveProvider = viewModel::saveProvider,
                onSetActiveProvider = viewModel::setActiveProvider,
                onDeleteProvider = viewModel::deleteProvider,
                onLogin = viewModel::login,
                onRegister = viewModel::register,
                onLogout = viewModel::logout,
                onSyncCloud = viewModel::syncCloudNow,
                onClearAuthError = viewModel::clearAuthError,
            )
        }
        }

        state.update?.let { update ->
            UpdateDialog(
                update = update,
                currentVersion = BuildConfig.VERSION_NAME,
                download = state.updateDownload,
                onDownload = { viewModel.downloadUpdate(update) },
                onBrowse = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                    )
                },
                onInstall = {
                    val ready = state.updateDownload as? UpdateDownloadState.Ready
                    if (ready != null) installDownloadedApk(context, ready.filePath)
                },
                onDismiss = viewModel::dismissUpdate,
            )
        }

        ReinSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 68.dp)
                .zIndex(20f),
        )
    }
}
