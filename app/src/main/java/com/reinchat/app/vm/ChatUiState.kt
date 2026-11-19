package com.reinchat.app.vm

import com.reinchat.app.data.AppSettings
import com.reinchat.app.data.ChatImage
import com.reinchat.app.data.CloudAccount
import com.reinchat.app.data.Conversation
import com.reinchat.app.data.PdfAttachment
import com.reinchat.app.data.PendingAttachment
import com.reinchat.app.data.PendingAttachmentKind
import com.reinchat.app.data.ProviderConfig
import com.reinchat.app.data.ProviderModel
import com.reinchat.app.data.ThemeMode
import com.reinchat.app.update.AppUpdate
import com.reinchat.app.update.UpdateDownloadState

/** WAITING = request in flight, no output yet; STREAMING = text is arriving. */
enum class StreamPhase {
    IDLE,
    WAITING,
    STREAMING,
}

data class StreamingSession(
    val conversationId: String,
    val messageId: String,
    val startedAt: Long,
    val phase: StreamPhase = StreamPhase.WAITING,
)

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val settings: AppSettings = AppSettings(),
    val draft: String = "",
    val attachments: List<ChatImage> = emptyList(),
    /** Single PDF attached to the composer (web client parity). */
    val pdfAttachment: PdfAttachment? = null,
    val pdfBusy: Boolean = false,
    val pdfError: String? = null,
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

    /** Current assistant stream phase; IDLE when nothing is being generated. */
    val streamPhase: StreamPhase
        get() = streaming?.phase ?: StreamPhase.IDLE

    /** True while the request is in flight but no token has arrived yet. */
    val isWaitingForFirstToken: Boolean
        get() = streamPhase == StreamPhase.WAITING

    val fontScale: Float
        get() = settings.fontScale

    val themeMode: ThemeMode
        get() = settings.themeMode

    /** Model directory for the active provider (empty when unknown). */
    val providerModels: List<ProviderModel>
        get() = activeProvider?.models.orEmpty()

    /** Unified composer attachment list for new UI code; old fields stay intact. */
    val pendingAttachments: List<PendingAttachment>
        get() {
            val images = attachments.map { image ->
                PendingAttachment(
                    id = image.id,
                    kind = PendingAttachmentKind.IMAGE,
                    uri = image.sourceUri ?: image.path,
                    previewPath = image.path,
                    name = image.name,
                    mimeType = image.mimeType,
                    width = image.width,
                    height = image.height,
                )
            }
            val pdf = pdfAttachment?.let { attachment ->
                listOf(
                    PendingAttachment(
                        id = attachment.id,
                        kind = PendingAttachmentKind.PDF,
                        uri = attachment.uri,
                        previewPath = attachment.localPath.takeIf { it.isNotBlank() },
                        name = attachment.name,
                        mimeType = attachment.mimeType,
                        text = attachment.text,
                        pages = attachment.pages,
                        ocrUsed = attachment.ocrUsed,
                        state = attachment.state,
                        error = attachment.error,
                    )
                )
            }.orEmpty()
            return images + pdf
        }

    val canSend: Boolean
        get() = !isStreaming &&
            (draft.isNotBlank() || attachments.isNotEmpty() || pdfAttachment != null)

    val canAttach: Boolean
        get() = !isStreaming && attachments.size < MAX_ATTACHMENT_IMAGES && !pdfBusy
}

/** Mirrors ChatViewModel.MAX_IMAGES; kept here so UI can disable the picker. */
const val MAX_ATTACHMENT_IMAGES = 4

sealed interface ChatEvent {
    data class OpenSettings(val focusProviderId: String? = null) : ChatEvent
    data class Notice(val message: String) : ChatEvent
}
