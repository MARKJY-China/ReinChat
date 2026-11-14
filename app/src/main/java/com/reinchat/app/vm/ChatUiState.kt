package com.reinchat.app.vm

import com.reinchat.app.data.AppSettings
import com.reinchat.app.data.ChatImage
import com.reinchat.app.data.CloudAccount
import com.reinchat.app.data.Conversation
import com.reinchat.app.data.ProviderConfig
import com.reinchat.app.update.AppUpdate
import com.reinchat.app.update.UpdateDownloadState

data class StreamingSession(
    val conversationId: String,
    val messageId: String,
    val startedAt: Long,
)

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val settings: AppSettings = AppSettings(),
    val draft: String = "",
    val attachments: List<ChatImage> = emptyList(),
    val editingMessageId: String? = null,
    val importingImage: Boolean = false,
    val webSearch: Boolean = true,
    val streaming: StreamingSession? = null,
    val newChatCloud: Boolean = false,
    val cloudSyncing: Boolean = false,
    val cloudError: String? = null,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val update: AppUpdate? = null,
    val updateDownload: UpdateDownloadState = UpdateDownloadState.Idle,
) {
    val activeConversation: Conversation?
        get() = conversations.firstOrNull { it.id == activeConversationId }

    val activeProvider: ProviderConfig?
        get() = settings.activeProvider

    val account: CloudAccount?
        get() = settings.cloudAccount

    val isLoggedIn: Boolean
        get() = account != null

    val isStreaming: Boolean
        get() = streaming != null

    val canSend: Boolean
        get() = !isStreaming && (draft.isNotBlank() || attachments.isNotEmpty())
}

sealed interface ChatEvent {
    data class OpenSettings(val focusProviderId: String? = null) : ChatEvent
    data class Notice(val message: String) : ChatEvent
}
