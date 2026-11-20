package com.reinchat.app.vm

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reinchat.app.data.AppSettings
import com.reinchat.app.data.ChatImage
import com.reinchat.app.data.ChatMessage
import com.reinchat.app.data.CloudAccount
import com.reinchat.app.data.Conversation
import com.reinchat.app.data.ConversationStore
import com.reinchat.app.data.ImageImportException
import com.reinchat.app.data.ImageStore
import com.reinchat.app.data.MessageRole
import com.reinchat.app.data.MessageSegment
import com.reinchat.app.data.MessageStatus
import com.reinchat.app.data.PdfAttachment
import com.reinchat.app.data.PdfAttachmentState
import com.reinchat.app.data.PdfImportException
import com.reinchat.app.data.PdfStore
import com.reinchat.app.data.PdfTextExtractor
import com.reinchat.app.data.PdfTextPolicy
import com.reinchat.app.data.ProcessTimeline
import com.reinchat.app.data.ProviderConfig
import com.reinchat.app.data.ProviderModel
import com.reinchat.app.data.SecureVault
import com.reinchat.app.data.SettingsStore
import com.reinchat.app.data.ThemeMode
import com.reinchat.app.data.friendlyError
import com.reinchat.app.data.isDefaultConversationTitle
import com.reinchat.app.data.modelOptionsOf
import com.reinchat.app.data.newId
import com.reinchat.app.data.titleFromPrompt
import com.reinchat.app.BuildConfig
import com.reinchat.app.net.ApiEndpoint
import com.reinchat.app.net.ApiException
import com.reinchat.app.net.ChatApiClient
import com.reinchat.app.net.CloudApiClient
import com.reinchat.app.net.CloudApiException
import com.reinchat.app.net.CloudAuthException
import com.reinchat.app.net.CloudConflictException
import com.reinchat.app.net.CloudEndpoint
import com.reinchat.app.net.CloudMessageDto
import com.reinchat.app.net.CloudSyncEventDto
import com.reinchat.app.net.RelayCancelledException
import com.reinchat.app.net.normalizeApiKey
import com.reinchat.app.net.ConversationDto
import com.reinchat.app.net.StreamEvent
import com.reinchat.app.net.friendlyNetworkError
import com.reinchat.app.net.roleEnum
import com.reinchat.app.net.statusEnum
import com.reinchat.app.net.toDto
import com.reinchat.app.net.toLocalModel
import com.reinchat.app.net.toLocalSegment
import com.reinchat.app.update.AppUpdate
import com.reinchat.app.update.UpdateChecker
import com.reinchat.app.update.UpdateDownloadState
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.reinchat.app.net.toLocalProvider
import com.reinchat.app.service.ChatStreamService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MAX_IMAGES = 4
        private const val STREAM_SAVE_INTERVAL_MS = 850L
        private const val STREAM_FRAME_MS = 16L
        private const val STREAM_MIN_CHARS_PER_SECOND = 110.0
        private const val STREAM_MAX_CHARS_PER_SECOND = 760.0
        private const val STREAM_BACKLOG_FACTOR = 4.0
        private const val TITLE_WAIT_FOR_CLOUD_MS = 20_000L
    }

    private val app = application
    private val imageStore = ImageStore(application)
    private val pdfStore = PdfStore(application)
    private val settingsStore = SettingsStore(application, SecureVault(application))
    private val conversationStore = ConversationStore(application)
    private val api = ChatApiClient(imageStore)
    private val cloudApi = CloudApiClient()
    private val cloudMutex = Mutex()

    private val initialSettings = settingsStore.load().let { loaded ->
        loaded.copy(providers = loaded.providers.map { provider ->
            if (provider.reasoningEffort.isBlank()) {
                provider.copy(reasoningEffort = "high")
            } else {
                provider
            }
        })
    }
    private val initialConversations = conversationStore.load()
        .filter { !it.cloud || initialSettings.cloudAccount != null }

    private val _state = MutableStateFlow(
        ChatUiState(
            conversations = initialConversations,
            activeConversationId = initialConversations.firstOrNull()?.id,
            settings = initialSettings,
            newChatCloud = initialSettings.cloudAccount != null,
        )
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val _events = Channel<ChatEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val conversationSaves = Channel<List<Conversation>>(Channel.CONFLATED)

    private var streamJob: Job? = null
    private var activeStreamId: String? = null
    private var activeRelayJobId: String? = null
    private var relayCancelRequested = false
    private var importJob: Job? = null
    private var pdfJob: Job? = null
    private var titleJob: Job? = null
    private var authJob: Job? = null
    private var fullCloudSyncJob: Job? = null
    private var cloudEventsJob: Job? = null
    private var updateCheckInFlight = false
    private var lastUpdateCheckAt = 0L
    private var updateDownloadJob: Job? = null
    private val updateHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var rewindUndo: RewindUndo? = null
    private var editBackup: ComposerBackup? = null
    private var lastCloudSyncAt = 0L
    private val imagesDir by lazy { File(app.filesDir, "images").apply { mkdirs() } }

    private data class RewindUndo(
        val conversation: Conversation,
        val activeConversationId: String?,
    )

    private data class ComposerBackup(
        val draft: String,
        val attachments: List<ChatImage>,
        val pdf: PdfAttachment?,
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (snapshot in conversationSaves) {
                conversationStore.save(snapshot)
            }
        }
        if (initialSettings.cloudAccount != null) {
            viewModelScope.launch {
                delay(450)
                syncCloudData(showNoticeOnError = false)
            }
        }
    }

    fun setDraft(value: String) {
        _state.update { it.copy(draft = value) }
    }

    /** Global UI scale, clamped to the range the web client exposes (85%..125%). */
    fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.85f, 1.30f)
        if (_state.value.settings.fontScale == clamped) return
        _state.update { it.copy(settings = it.settings.copy(fontScale = clamped)) }
        persistSettings()
    }

    fun setThemeMode(mode: ThemeMode) {
        if (_state.value.settings.themeMode == mode) return
        _state.update { it.copy(settings = it.settings.copy(themeMode = mode)) }
        persistSettings()
    }

    fun setThemeMode(mode: String) {
        val parsed = runCatching { ThemeMode.valueOf(mode.trim().uppercase()) }.getOrNull()
            ?: return
        setThemeMode(parsed)
    }

    fun toggleThemeMode(dark: Boolean) {
        setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            val available = (MAX_IMAGES - _state.value.attachments.size).coerceAtLeast(0)
            if (available == 0) {
                notice("每轮对话最多添加 $MAX_IMAGES 张图片")
                return@launch
            }
            _state.update { it.copy(importingImage = true) }
            var added = 0
            for (uri in uris.take(available)) {
                try {
                    val image = imageStore.import(uri).copy(sourceUri = uri.toString())
                    _state.update { current ->
                        if (current.attachments.size >= MAX_IMAGES) current
                        else current.copy(attachments = current.attachments + image)
                    }
                    added++
                } catch (e: ImageImportException) {
                    notice(e.message ?: "图片导入失败")
                } catch (e: OutOfMemoryError) {
                    notice("图片太大，无法导入")
                } catch (e: Throwable) {
                    Log.w(TAG, "Image import failed", e)
                    notice("图片导入失败，请换一张试试")
                }
            }
            _state.update { it.copy(importingImage = false) }
            if (uris.size > available && added == available) {
                notice("每轮对话最多添加 $MAX_IMAGES 张图片")
            }
        }
    }

    /**
     * Intake for the image picker / drag-and-drop. Same behavior as [addImages];
     * named explicitly so new UI code can distinguish images from PDFs.
     */
    fun attachImages(uris: List<Uri>) = addImages(uris)

    /**
     * Copies [uri] locally, tries a local text extraction and falls back to the
     * server OCR endpoint when the PDF has no usable text layer. The composer
     * state exposes progress through [ChatUiState.pdfBusy] / [ChatUiState.pdfError].
     *
     * The fallback decision itself lives in [PdfTextPolicy] so it is unit-tested
     * and can be exercised by the debug OCR probe. When the user is signed out
     * the attachment is still accepted and the UI explains that OCR needs login.
     */
    fun attachPdf(uri: Uri) {
        if (pdfJob?.isActive == true) {
            notice("PDF 正在解析，请稍候")
            return
        }
        pdfJob = viewModelScope.launch {
            _state.update { it.copy(pdfBusy = true, pdfError = null) }
            var stored: PdfStore.StoredPdf? = null
            try {
                stored = withContext(Dispatchers.IO) { pdfStore.import(uri) }
                val previousPath = _state.value.pdfAttachment?.localPath.orEmpty()
                var text = ""
                var pages = 0
                try {
                    val local = withContext(Dispatchers.IO) { PdfTextExtractor.extract(stored.file) }
                    text = local.text
                    pages = local.pages
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // A huge or malformed PDF may fail the local pass; the
                    // server OCR fallback can still extract something.
                    Log.w(TAG, "Local PDF extraction failed", e)
                }

                val account = _state.value.account
                val decision = PdfTextPolicy.decide(text, account != null)
                var ocrUsed = false
                val provisional = PdfAttachment(
                    uri = uri.toString(),
                    localPath = stored.file.absolutePath,
                    name = stored.name,
                    text = text,
                    pages = pages,
                    state = decision.pendingState,
                    error = decision.message,
                )
                _state.update {
                    it.copy(
                        pdfAttachment = provisional,
                        pdfBusy = true,
                        pdfError = decision.message,
                    )
                }
                if (decision.blockedByLogin) {
                    // Keep the attachment: the text field stays empty and `send`
                    // is still allowed, but the UI explains why OCR is missing.
                    notice("未检测到文字层：登录后可使用云端 OCR，附件仍可发送")
                }
                if (decision.needsServerOcr && account != null) {
                    val ocr = withContext(Dispatchers.IO) {
                        cloudApi.ocrPdf(account, stored.file, stored.mimeType)
                    }
                    text = ocr.text.trim()
                    pages = if (ocr.pages > 0) ocr.pages else pages
                    ocrUsed = text.isNotBlank()
                }

                val attachment = provisional.copy(
                    text = text,
                    pages = pages,
                    ocrUsed = ocrUsed,
                    state = if (text.isBlank()) {
                        PdfAttachmentState.EMPTY
                    } else {
                        PdfAttachmentState.READY
                    },
                    error = when {
                        text.isNotBlank() -> null
                        decision.needsServerOcr -> PdfTextPolicy.OCR_EMPTY_MESSAGE
                        else -> decision.message
                    },
                )
                _state.update {
                    it.copy(
                        pdfAttachment = attachment,
                        pdfBusy = false,
                        pdfError = attachment.error,
                    )
                }
                if (previousPath.isNotBlank() && previousPath != attachment.localPath) {
                    withContext(Dispatchers.IO) { pdfStore.delete(previousPath) }
                }
            } catch (cancel: CancellationException) {
                stored?.file?.delete()
                throw cancel
            } catch (e: PdfImportException) {
                _state.update { it.copy(pdfBusy = false, pdfError = e.message) }
                notice(e.message ?: "PDF 导入失败")
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(pdfBusy = false, pdfError = "PDF 太大，无法解析") }
                notice("PDF 太大，无法解析")
            } catch (e: Throwable) {
                Log.w(TAG, "PDF attach failed", e)
                val message = when (e) {
                    is CloudAuthException -> "登录已过期，无法使用云端 OCR；附件仍会随消息发送。"
                    is CloudApiException -> PdfTextPolicy.ocrFailedMessage(e.message)
                    else -> "PDF 解析失败，请重试"
                }
                _state.update { state ->
                    state.copy(
                        pdfBusy = false,
                        pdfError = message,
                        pdfAttachment = state.pdfAttachment?.copy(
                            state = PdfAttachmentState.EMPTY,
                            error = message,
                        ),
                    )
                }
                notice(message)
            } finally {
                pdfJob = null
            }
        }
    }

    /** Removes an image by id, or the attached PDF when its id/uri matches. */
    fun removeAttachment(imageId: String) {
        val snapshot = _state.value
        val pdf = snapshot.pdfAttachment
        if (pdf != null &&
            (imageId == pdf.id || imageId == "pdf" || imageId == pdf.uri)
        ) {
            removePdfAttachment()
            return
        }
        _state.update { state ->
            state.copy(attachments = state.attachments.filterNot { it.id == imageId })
        }
    }

    fun removePdfAttachment() {
        val pdf = _state.value.pdfAttachment ?: return
        pdfJob?.cancel()
        pdfJob = null
        _state.update {
            it.copy(pdfAttachment = null, pdfBusy = false, pdfError = null)
        }
        if (pdf.localPath.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) { pdfStore.delete(pdf.localPath) }
        }
    }

    fun clearPdfError() {
        _state.update { it.copy(pdfError = null) }
    }

    /**
     * Clipboard intake. Compose exposes the raw [ClipData]; call this from the
     * paste gesture. Returns true when at least one attachment was accepted.
     */
    fun pasteFromClipboard(clip: ClipData?): Boolean {
        if (clip == null || clip.itemCount == 0) return false
        val uris = buildList {
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let(::add)
            }
        }
        return handlePastedUris(uris)
    }

    /**
     * Clipboard intake when the platform only provides a [ClipDescription]
     * plus separately resolved URIs (e.g. drag-and-drop or a permission-gated
     * clipboard read). Accepts only image or PDF payloads.
     */
    fun attachFromClipDescription(description: ClipDescription?, uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        if (description != null) {
            val supported = description.hasMimeType("image/*") ||
                description.hasMimeType("application/pdf") ||
                uris.any { looksLikePdfUri(it) }
            if (!supported) return false
        }
        return handlePastedUris(uris)
    }

    /** Routes a mixed list of image/PDF URIs to the right attachment slot. */
    fun handlePastedUris(uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        val resolver = app.contentResolver
        val images = mutableListOf<Uri>()
        var pdfUri: Uri? = null
        var rejected = 0
        for (uri in uris) {
            val mime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty().lowercase()
            val name = uri.lastPathSegment.orEmpty().lowercase()
            when {
                mime.startsWith("image/") -> images += uri
                mime.contains("pdf") || name.endsWith(".pdf") -> if (pdfUri == null) pdfUri = uri
                else -> rejected++
            }
        }
        if (images.isNotEmpty()) addImages(images)
        pdfUri?.let { attachPdf(it) }
        if (images.isEmpty() && pdfUri == null) {
            if (rejected > 0) notice("仅支持图片或 PDF 附件")
            return false
        }
        return true
    }

    private fun looksLikePdfUri(uri: Uri): Boolean {
        val mime = runCatching { app.contentResolver.getType(uri) }
            .getOrNull().orEmpty().lowercase()
        return mime.contains("pdf") || uri.lastPathSegment?.lowercase()?.endsWith(".pdf") == true
    }

    fun send() {
        val snapshot = _state.value
        if (snapshot.isStreaming) {
            notice("正在生成回复，请先停止")
            return
        }
        val pdf = snapshot.pdfAttachment
        if (pdf != null && pdf.busiest) {
            notice("PDF 还在解析，请稍候")
            return
        }
        val text = snapshot.draft.trim().ifBlank {
            when {
                pdf != null && pdf.hasText -> "请阅读并分析这份 PDF。"
                // Keep the attachment sendable even when OCR was unavailable,
                // but never imply the model can read an empty text layer.
                pdf != null -> "我附上了一份 PDF，但当前没有提取到文字（可能是扫描件），无法读取内容。"
                snapshot.attachments.isNotEmpty() -> "请分析这张图片。"
                else -> ""
            }
        }
        if (text.isBlank() && snapshot.attachments.isEmpty() && pdf == null) return

        val provider = snapshot.activeProvider
        if (provider == null) {
            _events.trySend(ChatEvent.OpenSettings())
            notice("先配置一个 AI 服务商")
            return
        }
        val baseError = ApiEndpoint.validationError(provider.baseUrl)
        if (baseError != null || provider.activeModel.isBlank()) {
            _events.trySend(ChatEvent.OpenSettings(provider.id))
            notice(baseError ?: "请填写模型名称")
            return
        }

        val now = System.currentTimeMillis()
        val existing = snapshot.activeConversation
        var baseMessages = existing?.messages.orEmpty()
        var title = existing?.title

        val editingId = snapshot.editingMessageId
        if (editingId != null && existing != null) {
            val index = baseMessages.indexOfFirst { it.id == editingId && it.role == MessageRole.USER }
            if (index >= 0) {
                val oldFirstUser = baseMessages.firstOrNull { it.role == MessageRole.USER }
                val autoTitle = oldFirstUser == null || existing.title ==
                    titleFromPrompt(oldFirstUser.content, oldFirstUser.images.size)
                baseMessages = baseMessages.take(index)
                if (baseMessages.isEmpty() && autoTitle) {
                    title = titleFromPrompt(text, snapshot.attachments.size)
                }
            }
        }

        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = text,
            images = snapshot.attachments,
            pdfName = pdf?.name.orEmpty(),
            pdfText = pdf?.text.orEmpty(),
            createdAt = now,
        )
        val assistantMessage = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            model = provider.activeModel,
            createdAt = now + 1,
            status = MessageStatus.STREAMING,
        )
        val finalMessages = baseMessages + userMessage + assistantMessage
        val finalTitle = title?.takeIf { it.isNotBlank() }
            ?: titleFromPrompt(text, snapshot.attachments.size)
        val cloud = existing?.cloud ?: (snapshot.isLoggedIn && snapshot.newChatCloud)
        val conversation = existing?.copy(
            title = finalTitle,
            messages = finalMessages,
            updatedAt = now,
        ) ?: Conversation(
            title = finalTitle,
            createdAt = now,
            updatedAt = now,
            messages = finalMessages,
            cloud = cloud,
            cloudVersion = 0,
        )

        val conversations = if (existing == null) {
            listOf(conversation) + snapshot.conversations
        } else {
            snapshot.conversations.map { if (it.id == conversation.id) conversation else it }
        }.sortedByDescending { it.updatedAt }

        editBackup = null
        rewindUndo = null
        _state.update {
            it.copy(
                conversations = conversations,
                activeConversationId = conversation.id,
                draft = "",
                attachments = emptyList(),
                pdfAttachment = null,
                pdfBusy = false,
                pdfError = null,
                editingMessageId = null,
                newChatCloud = false,
            )
        }
        // The extracted text is now stored on the message, so the cached PDF
        // file is no longer needed.
        if (pdf != null && pdf.localPath.isNotBlank()) {
            val path = pdf.localPath
            viewModelScope.launch(Dispatchers.IO) { pdfStore.delete(path) }
        }
        persistConversations()
        if (conversation.cloud) {
            scheduleCloudConversationSync(conversation.id)
        }
        startStream(
            provider = provider,
            conversationId = conversation.id,
            assistantId = assistantMessage.id,
            history = finalMessages.dropLast(1),
        )
    }

    fun setActiveModel(providerId: String, model: String) {
        val current = _state.value.settings
        val provider = current.providers.firstOrNull { it.id == providerId } ?: return
        if (model !in provider.modelOptions) return
        val providers = current.providers.map {
            if (it.id == providerId) it.copy(selectedModel = model) else it
        }
        _state.update { it.copy(settings = it.settings.copy(providers = providers)) }
        persistSettings()
    }

    fun setReasoningEffort(providerId: String, effort: String) {
        if (effort !in setOf("", "low", "high", "max")) return
        val current = _state.value.settings
        if (current.providers.none { it.id == providerId }) return
        val providers = current.providers.map {
            if (it.id == providerId) it.copy(reasoningEffort = effort) else it
        }
        _state.update { it.copy(settings = it.settings.copy(providers = providers)) }
        persistSettings()
    }

    /**
     * Regenerates the answer for the user message preceding [messageId].
     * The failed assistant message and anything after it are discarded.
     */
    fun retryResponse(messageId: String) {
        val snapshot = _state.value
        if (snapshot.isStreaming) {
            notice("正在生成回复，请先停止")
            return
        }
        val conversation = snapshot.activeConversation ?: return
        val assistantIndex = conversation.messages.indexOfFirst {
            it.id == messageId && it.role == MessageRole.ASSISTANT
        }
        if (assistantIndex < 0) return
        val userIndex = conversation.messages
            .subList(0, assistantIndex)
            .indexOfLast { it.role == MessageRole.USER }
        if (userIndex < 0) {
            notice("找不到对应的提问")
            return
        }
        val provider = snapshot.activeProvider
        if (provider == null) {
            _events.trySend(ChatEvent.OpenSettings())
            notice("先配置一个 AI 服务商")
            return
        }
        val baseError = ApiEndpoint.validationError(provider.baseUrl)
        if (baseError != null || provider.activeModel.isBlank()) {
            _events.trySend(ChatEvent.OpenSettings(provider.id))
            notice(baseError ?: "请填写模型名称")
            return
        }

        val now = System.currentTimeMillis()
        val history = conversation.messages.take(userIndex + 1)
        val assistantMessage = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            model = provider.activeModel,
            createdAt = now + 1,
            status = MessageStatus.STREAMING,
        )
        val updated = conversation.copy(
            messages = history + assistantMessage,
            updatedAt = now,
        )
        val conversations = snapshot.conversations
            .map { if (it.id == updated.id) updated else it }
            .sortedByDescending { it.updatedAt }
        editBackup = null
        rewindUndo = null
        _state.update {
            it.copy(
                conversations = conversations,
                activeConversationId = updated.id,
                draft = "",
                attachments = emptyList(),
                pdfAttachment = null,
                pdfBusy = false,
                pdfError = null,
                editingMessageId = null,
            )
        }
        persistConversations()
        if (updated.cloud) scheduleCloudConversationSync(updated.id)
        startStream(
            provider = provider,
            conversationId = updated.id,
            assistantId = assistantMessage.id,
            history = history,
        )
    }

    /** Kept for existing UI bindings; performs the full stop flow. */
    fun stopStreaming() = stopGeneration()

    /**
     * Stops generation locally (cancels the SSE coroutine) and asks the server
     * to cancel the relay job so it does not keep consuming provider quota.
     * Partial text is preserved and the message becomes CANCELLED without error.
     */
    fun stopGeneration() {
        if (_state.value.streaming == null) return
        if (streamJob?.isActive != true) return
        relayCancelRequested = true
        streamJob?.cancel()
        val jobId = activeRelayJobId
        activeRelayJobId = null
        val account = _state.value.account
        if (!jobId.isNullOrBlank() && account != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { cloudApi.cancelRelayJob(account, jobId) }
                    .onFailure { Log.w(TAG, "Relay job cancel failed", it) }
            }
        }
    }

    private fun startStream(
        provider: ProviderConfig,
        conversationId: String,
        assistantId: String,
        history: List<ChatMessage>,
    ) {
        streamJob?.cancel()
        activeStreamId = assistantId
        activeRelayJobId = null
        relayCancelRequested = false
        ChatStreamService.start(app)
        _state.update {
            it.copy(
                streaming = StreamingSession(
                    conversationId = conversationId,
                    messageId = assistantId,
                    startedAt = System.currentTimeMillis(),
                    phase = StreamPhase.WAITING,
                )
            )
        }
        streamJob = viewModelScope.launch {
            val received = StringBuilder()
            val receivedReasoning = StringBuilder()
            val timeline = ProcessTimeline()
            var shownChars = 0
            var shownReasoningChars = 0
            var lastSave = System.currentTimeMillis()
            var renderJob: Job? = null

            // SSE deltas arrive in irregular chunks. Keep a small render loop
            // that drains the buffers at an adaptive, frame-paced rate instead
            // of replacing the whole message on every network packet.
            fun publishVisibleText() {
                if (shownChars <= 0 && shownReasoningChars <= 0 && timeline.isEmpty) return
                updateStreamingContent(
                    conversationId = conversationId,
                    messageId = assistantId,
                    text = received.substring(0, shownChars),
                    reasoning = receivedReasoning.substring(0, shownReasoningChars),
                    segments = timeline.segments,
                )
            }

            fun markStreamingPhase() {
                val current = _state.value.streaming ?: return
                if (current.messageId != assistantId || current.phase == StreamPhase.STREAMING) return
                _state.update { it.copy(streaming = current.copy(phase = StreamPhase.STREAMING)) }
            }

            fun flushAllReceived() {
                val contentChanged = shownChars < received.length
                val reasoningChanged = shownReasoningChars < receivedReasoning.length
                if (!contentChanged && !reasoningChanged) return
                shownChars = received.length
                shownReasoningChars = receivedReasoning.length
                publishVisibleText()
            }

            renderJob = launch {
                var lastFrame = System.nanoTime()
                while (isActive) {
                    delay(STREAM_FRAME_MS)
                    val now = System.nanoTime()
                    val elapsed = ((now - lastFrame) / 1_000_000_000.0).coerceIn(0.001, 0.08)
                    lastFrame = now
                    val pendingContent = received.length - shownChars
                    val pendingReasoning = receivedReasoning.length - shownReasoningChars
                    val pending = max(pendingContent, pendingReasoning)
                    if (pending <= 0) continue
                    val rate = (STREAM_MIN_CHARS_PER_SECOND + pending * STREAM_BACKLOG_FACTOR)
                        .coerceAtMost(STREAM_MAX_CHARS_PER_SECOND)
                    val requestedStep = max(1, (rate * elapsed).roundToInt())

                    if (pendingContent > 0) {
                        shownChars += requestedStep.coerceAtMost(pendingContent)
                        if (shownChars < received.length &&
                            Character.isHighSurrogate(received[shownChars - 1])
                        ) {
                            shownChars -= 1
                        }
                    }
                    if (pendingReasoning > 0) {
                        shownReasoningChars += requestedStep.coerceAtMost(pendingReasoning)
                        if (shownReasoningChars < receivedReasoning.length &&
                            Character.isHighSurrogate(receivedReasoning[shownReasoningChars - 1])
                        ) {
                            shownReasoningChars -= 1
                        }
                    }
                    publishVisibleText()
                    val wallClock = System.currentTimeMillis()
                    if (wallClock - lastSave > STREAM_SAVE_INTERVAL_MS) {
                        persistConversations()
                        lastSave = wallClock
                    }
                }
            }

            try {
                val relayAccount = _state.value.account?.takeIf { it.level >= 2 }
                val streamHistory = if (relayAccount != null) {
                    prepareRelayImages(relayAccount, conversationId, history)
                } else {
                    history
                }
                val events = if (relayAccount != null) {
                    if (provider.localOnly || !provider.cloudSynced) {
                        withContext(Dispatchers.IO) {
                            cloudApi.putProvider(relayAccount, provider.toDto())
                        }
                        markProviderSynced(provider.id)
                    }
                    cloudApi.relay(
                        account = relayAccount,
                        providerId = provider.id,
                        conversationId = conversationId,
                        model = provider.activeModel,
                        reasoningEffort = provider.reasoningEffort,
                        search = _state.value.webSearch,
                        messages = streamHistory,
                        onJobStarted = { jobId ->
                            activeRelayJobId = jobId
                            // The user may have pressed stop while the job was
                            // being created; cancel it as soon as it exists.
                            if (relayCancelRequested) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    runCatching { cloudApi.cancelRelayJob(relayAccount, jobId) }
                                        .onFailure { Log.w(TAG, "Relay job cancel failed", it) }
                                }
                            }
                        },
                    )
                } else {
                    api.stream(provider, streamHistory)
                }
                events.collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> {
                            received.append(event.text)
                            timeline.appendText(event.text)
                            markStreamingPhase()
                        }
                        is StreamEvent.Reasoning -> {
                            receivedReasoning.append(event.text)
                            timeline.appendReasoning(event.text)
                            markStreamingPhase()
                            publishVisibleText()
                        }
                        is StreamEvent.Tool -> {
                            timeline.upsertTool(event.name, event.status, event.detail)
                            markStreamingPhase()
                            publishVisibleText()
                        }
                        is StreamEvent.Snapshot -> {
                            // Job state replayed when the event stream opens.
                            // Tool history is seeded in order; text/reasoning are
                            // only applied when nothing arrived live yet, so a
                            // reconnect never duplicates content.
                            event.tools.forEach { tool ->
                                timeline.upsertTool(tool.name, tool.status, tool.detail)
                            }
                            if (received.isEmpty() && event.text.isNotEmpty()) {
                                received.append(event.text)
                                timeline.appendText(event.text)
                            }
                            if (receivedReasoning.isEmpty() && event.reasoning.isNotEmpty()) {
                                receivedReasoning.append(event.reasoning)
                                timeline.seedReasoning(event.reasoning)
                            }
                            if (event.tools.isNotEmpty() ||
                                received.isNotEmpty() ||
                                event.reasoning.isNotEmpty()
                            ) {
                                markStreamingPhase()
                                publishVisibleText()
                            }
                        }
                        StreamEvent.Completed -> Unit
                    }
                }
                renderJob?.cancel()
                flushAllReceived()
                if (received.isBlank() && receivedReasoning.isBlank()) {
                    throw ApiException("服务商没有返回可显示的内容")
                }
                if (received.isBlank()) {
                    finishAssistant(
                        conversationId = conversationId,
                        assistantId = assistantId,
                        status = MessageStatus.ERROR,
                        content = "",
                        reasoning = receivedReasoning.toString(),
                        segments = timeline.segments,
                        error = "服务商只返回了思考过程，没有生成正文",
                    )
                } else {
                    finishAssistant(
                        conversationId = conversationId,
                        assistantId = assistantId,
                        status = MessageStatus.COMPLETE,
                        content = received.toString(),
                        reasoning = receivedReasoning.toString(),
                        segments = timeline.segments,
                        error = null,
                    )
                }
            } catch (cancel: CancellationException) {
                renderJob?.cancel()
                flushAllReceived()
                finishAssistant(
                    conversationId = conversationId,
                    assistantId = assistantId,
                    status = MessageStatus.CANCELLED,
                    content = received.toString(),
                    reasoning = receivedReasoning.toString(),
                    segments = timeline.segments,
                    error = null,
                )
                throw cancel
            } catch (error: Throwable) {
                renderJob?.cancel()
                flushAllReceived()
                val cancelled = error is RelayCancelledException
                finishAssistant(
                    conversationId = conversationId,
                    assistantId = assistantId,
                    status = if (cancelled) MessageStatus.CANCELLED else MessageStatus.ERROR,
                    content = received.toString(),
                    reasoning = receivedReasoning.toString(),
                    segments = timeline.segments,
                    error = if (cancelled) null else friendlyStreamError(error),
                )
            } finally {
                renderJob?.cancel()
                if (activeStreamId == assistantId) {
                    activeStreamId = null
                    activeRelayJobId = null
                    relayCancelRequested = false
                    streamJob = null
                }
            }
        }
    }

    /**
     * Relay image parts are read from the server file store by their file id,
     * so every outgoing image must be uploaded first. The returned history uses
     * images with [ChatImage.remoteId] set, and the local conversation is
     * updated so a retry/re-sync does not upload the same bytes again.
     */
    private suspend fun prepareRelayImages(
        account: CloudAccount,
        conversationId: String,
        history: List<ChatMessage>,
    ): List<ChatMessage> {
        var changed = false
        val updated = history.map { message ->
            if (message.images.isEmpty()) return@map message
            var messageChanged = false
            val images = message.images.map { image ->
                if (!image.remoteId.isNullOrBlank()) return@map image
                val uploaded = cloudApi.uploadImage(account, image)
                messageChanged = true
                image.copy(
                    remoteId = uploaded.id,
                    mimeType = uploaded.mimeType.ifBlank { image.mimeType },
                )
            }
            if (messageChanged) {
                changed = true
                message.copy(images = images)
            } else {
                message
            }
        }
        if (!changed) return updated
        val updatedById = updated.associateBy { it.id }
        _state.update { state ->
            state.copy(
                conversations = state.conversations.map { conversation ->
                    if (conversation.id != conversationId) conversation
                    else conversation.copy(
                        messages = conversation.messages.map { message ->
                            updatedById[message.id] ?: message
                        }
                    )
                }
            )
        }
        persistConversations()
        return updated
    }

    private fun updateStreamingContent(
        conversationId: String,
        messageId: String,
        text: String,
        reasoning: String,
        segments: List<MessageSegment>? = null,
    ) {
        _state.update { state ->
            state.copy(
                conversations = state.conversations.map { conversation ->
                    if (conversation.id != conversationId) conversation
                    else conversation.copy(
                        messages = conversation.messages.map { message ->
                            if (message.id == messageId) {
                                message.copy(
                                    content = text,
                                    reasoning = reasoning,
                                    segments = segments ?: message.segments,
                                )
                            } else message
                        }
                    )
                }
            )
        }
    }

    private fun finishAssistant(
        conversationId: String,
        assistantId: String,
        status: MessageStatus,
        content: String,
        reasoning: String = "",
        segments: List<MessageSegment> = emptyList(),
        error: String?,
    ) {
        if (activeStreamId == assistantId) {
            ChatStreamService.stop(getApplication())
        }
        val now = System.currentTimeMillis()
        var changed = false
        val conversations = _state.value.conversations.map { conversation ->
            if (conversation.id != conversationId) conversation
            else {
                changed = true
                val messages = if (status.isCancelled &&
                    content.isBlank() && reasoning.isBlank() && segments.isEmpty()
                ) {
                    conversation.messages.filterNot { it.id == assistantId }
                } else {
                    conversation.messages.map { message ->
                        if (message.id == assistantId) message.copy(
                            content = content,
                            reasoning = reasoning,
                            segments = segments.ifEmpty { message.segments },
                            status = status,
                            error = error,
                        ) else message
                    }
                }
                conversation.copy(messages = messages, updatedAt = now)
            }
        }.sortedByDescending { it.updatedAt }

        _state.update { state ->
            state.copy(
                conversations = conversations,
                streaming = if (state.streaming?.messageId == assistantId) null else state.streaming,
            )
        }
        if (changed) {
            persistConversations()
            val finished = _state.value.conversations.firstOrNull { it.id == conversationId }
            if (finished?.cloud == true) scheduleCloudConversationSync(conversationId)
            if (status == MessageStatus.COMPLETE) maybeGenerateTitle(conversationId)
        }
    }

    private fun friendlyStreamError(error: Throwable): String = when (error) {
        is ApiException -> friendlyError(error.message)
        is ImageImportException -> error.message ?: "无法读取图片"
        is CloudAuthException -> "登录已过期，请重新登录"
        is CloudApiException -> friendlyError(error.message)
        else -> friendlyError(friendlyNetworkError(error))
    }

    /**
     * After the first completed exchange, ask the server to generate a title.
     * Non-blocking: the local [titleFromPrompt] fallback already set at send time
     * stays in place when the request fails or the conversation is not cloud-backed.
     *
     * A brand-new cloud conversation is uploaded asynchronously, so wait briefly
     * for [Conversation.cloudVersion] to appear before calling the title endpoint
     * (that endpoint requires the conversation to exist on the server).
     */
    private fun maybeGenerateTitle(conversationId: String) {
        val account = _state.value.account ?: return
        val initial = _state.value.conversations.firstOrNull { it.id == conversationId } ?: return
        if (!initial.cloud) return
        if (titleJob?.isActive == true) return
        titleJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var conversation = initial
                var waited = 0L
                while (conversation.cloudVersion <= 0 && waited < TITLE_WAIT_FOR_CLOUD_MS && isActive) {
                    delay(500)
                    waited += 500
                    conversation = _state.value.conversations
                        .firstOrNull { it.id == conversationId } ?: return@launch
                }
                if (conversation.cloudVersion <= 0) return@launch
                val users = conversation.messages.filter { it.role == MessageRole.USER }
                if (users.size != 1) return@launch
                val first = users.first()
                val firstText = first.content.ifBlank {
                    if (first.pdfText.isNotBlank()) "请根据这份 PDF 生成标题：${first.pdfName}" else ""
                }
                if (firstText.isBlank() || !isAutoTitle(conversation)) return@launch
                val provider = _state.value.activeProvider ?: return@launch
                if (provider.localOnly || provider.activeModel.isBlank()) return@launch
                if (_state.value.account?.token != account.token) return@launch
                val generated = cloudApi.generateConversationTitle(
                    account = account,
                    conversationId = conversationId,
                    providerId = provider.id,
                    model = provider.activeModel,
                    text = firstText,
                )
                if (generated.isNotBlank()) applyGeneratedTitle(conversationId, generated)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Throwable) {
                Log.w(TAG, "Conversation title generation failed", e)
                // Keep the local titleFromPrompt fallback.
            } finally {
                titleJob = null
            }
        }
    }

    /** True while the title is a placeholder or the local auto title. */
    private fun isAutoTitle(conversation: Conversation): Boolean {
        if (isDefaultConversationTitle(conversation.title)) return true
        val first = conversation.messages.firstOrNull { it.role == MessageRole.USER }
            ?: return false
        return conversation.title == titleFromPrompt(first.content, first.images.size)
    }

    private fun applyGeneratedTitle(conversationId: String, raw: String) {
        val title = raw.replace(Regex("\\s+"), " ").trim().take(60)
        if (title.isBlank() || isDefaultConversationTitle(title)) return
        val conversation = _state.value.conversations.firstOrNull { it.id == conversationId } ?: return
        if (!isAutoTitle(conversation)) return
        val conversations = _state.value.conversations.map {
            if (it.id == conversationId) it.copy(title = title) else it
        }.sortedByDescending { it.updatedAt }
        _state.update { it.copy(conversations = conversations) }
        persistConversations()
        if (conversation.cloud) scheduleCloudConversationSync(conversationId)
    }

    /**
     * Refreshes provider model directories from `/v1/relay/providers`, which
     * returns `models: [{id,name,...}]` for cloud-stored providers.
     */
    fun refreshProviderModels() {
        val account = _state.value.account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val remote = cloudApi.listRelayProviders(account)
                if (remote.isEmpty()) return@launch
                if (_state.value.account?.token != account.token) return@launch
                var changed = false
                val current = _state.value.settings
                val providers = current.providers.map providerLoop@ { local ->
                    val match = remote.firstOrNull { it.id == local.id } ?: return@providerLoop local
                    val models = match.models
                        .map { it.toLocalModel() }
                        .filter { it.id.isNotBlank() }
                    if (models.isEmpty()) return@providerLoop local
                    val modelList = if (match.model.isNotBlank()) {
                        match.model
                    } else {
                        models.joinToString(",") { it.id }
                    }
                    if (local.models == models && local.model == modelList) {
                        local
                    } else {
                        changed = true
                        local.copy(models = models, model = modelList)
                    }
                }
                if (!changed) return@launch
                _state.update { it.copy(settings = it.settings.copy(providers = providers)) }
                persistSettings()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (auth: CloudAuthException) {
                Log.w(TAG, "Provider models refresh auth expired", auth)
            } catch (e: Throwable) {
                Log.w(TAG, "Provider models refresh failed", e)
            }
        }
    }

    fun beginEdit(messageId: String) {
        if (_state.value.isStreaming) {
            notice("请先停止当前生成")
            return
        }
        val conversation = _state.value.activeConversation ?: return
        val message = conversation.messages.firstOrNull {
            it.id == messageId && it.role == MessageRole.USER
        } ?: return
        if (_state.value.editingMessageId == null) {
            editBackup = ComposerBackup(
                draft = _state.value.draft,
                attachments = _state.value.attachments,
                pdf = _state.value.pdfAttachment,
            )
        }
        val pdf = if (message.hasPdf) {
            PdfAttachment(
                name = message.pdfName.ifBlank { "文档.pdf" },
                text = message.pdfText,
                state = if (message.pdfText.isBlank()) {
                    PdfAttachmentState.EMPTY
                } else {
                    PdfAttachmentState.READY
                },
            )
        } else {
            null
        }
        _state.update {
            it.copy(
                draft = message.content,
                attachments = message.images,
                pdfAttachment = pdf,
                pdfBusy = false,
                pdfError = null,
                editingMessageId = message.id,
            )
        }
    }

    fun cancelEdit() {
        val backup = editBackup
        editBackup = null
        _state.update {
            it.copy(
                draft = backup?.draft.orEmpty(),
                attachments = backup?.attachments.orEmpty(),
                pdfAttachment = backup?.pdf,
                pdfBusy = false,
                pdfError = null,
                editingMessageId = null,
            )
        }
    }

    fun rewindTo(messageId: String): Boolean {
        if (_state.value.isStreaming) {
            notice("请先停止当前生成")
            return false
        }
        val conversation = _state.value.activeConversation ?: return false
        val index = conversation.messages.indexOfFirst { it.id == messageId }
        if (index < 0 || index >= conversation.messages.lastIndex) return false
        rewindUndo = RewindUndo(conversation, _state.value.activeConversationId)
        val updated = conversation.copy(
            messages = conversation.messages.take(index + 1),
            updatedAt = System.currentTimeMillis(),
        )
        val conversations = _state.value.conversations
            .map { if (it.id == conversation.id) updated else it }
            .sortedByDescending { it.updatedAt }
        _state.update {
            it.copy(
                conversations = conversations,
                editingMessageId = it.editingMessageId
                    ?.takeIf { id -> updated.messages.any { message -> message.id == id } },
            )
        }
        persistConversations()
        if (conversation.cloud) scheduleCloudConversationSync(conversation.id)
        return true
    }

    fun undoRewind(): Boolean {
        if (_state.value.isStreaming) return false
        val undo = rewindUndo ?: return false
        rewindUndo = null
        val conversations = _state.value.conversations
            .filterNot { it.id == undo.conversation.id }
            .plus(undo.conversation)
            .sortedByDescending { it.updatedAt }
        _state.update {
            it.copy(
                conversations = conversations,
                activeConversationId = undo.activeConversationId ?: undo.conversation.id,
            )
        }
        persistConversations()
        if (undo.conversation.cloud) scheduleCloudConversationSync(undo.conversation.id)
        return true
    }

    fun selectConversation(conversationId: String) {
        if (_state.value.conversations.none { it.id == conversationId }) return
        if (_state.value.activeConversationId == conversationId) return
        // A stream is tied to its conversation, not to the currently visible
        // screen. Switching conversations must not abort generation.
        editBackup = null
        _state.update {
            it.copy(
                activeConversationId = conversationId,
                draft = "",
                attachments = emptyList(),
                pdfAttachment = null,
                pdfBusy = false,
                pdfError = null,
                editingMessageId = null,
                newChatCloud = false,
            )
        }
        refreshActiveCloudConversation(conversationId)
    }

    fun newConversation() {
        editBackup = null
        rewindUndo = null
        _state.update {
            it.copy(
                activeConversationId = null,
                draft = "",
                attachments = emptyList(),
                pdfAttachment = null,
                pdfBusy = false,
                pdfError = null,
                editingMessageId = null,
                newChatCloud = it.isLoggedIn,
            )
        }
    }

    fun setNewChatCloud(enabled: Boolean) {
        val state = _state.value
        if (!state.isLoggedIn) {
            notice("登录后可开启云端保存")
            return
        }
        if (state.activeConversation != null || state.editingMessageId != null) {
            notice("只有新会话可以开启云端保存")
            return
        }
        _state.update { it.copy(newChatCloud = enabled) }
    }

    fun renameConversation(conversationId: String, title: String) {
        val trimmed = title.trim().take(80)
        if (trimmed.isBlank()) return
        val conversations = _state.value.conversations.map {
            if (it.id == conversationId) it.copy(title = trimmed) else it
        }
        _state.update { it.copy(conversations = conversations) }
        persistConversations()
        if (conversations.firstOrNull { it.id == conversationId }?.cloud == true) {
            scheduleCloudConversationSync(conversationId)
        }
    }

    fun deleteConversation(conversationId: String) {
        if (_state.value.streaming?.conversationId == conversationId) stopStreaming()
        val target = _state.value.conversations.firstOrNull { it.id == conversationId } ?: return
        if (target.cloud) {
            val current = _state.value.settings
            _state.update {
                it.copy(settings = current.copy(
                    pendingCloudConversationDeletes =
                    (current.pendingCloudConversationDeletes + target.id).distinct()
                ))
            }
            persistSettings()
            deleteCloudConversation(target.id)
        }
        val conversations = _state.value.conversations.filterNot { it.id == conversationId }
        val nextActive = if (_state.value.activeConversationId == conversationId) {
            conversations.firstOrNull()?.id
        } else _state.value.activeConversationId
        _state.update {
            it.copy(
                conversations = conversations,
                activeConversationId = nextActive,
                editingMessageId = null,
                draft = "",
                attachments = emptyList(),
            )
        }
        persistConversations()
        val images = target.messages.flatMap { it.images }
        viewModelScope.launch(Dispatchers.IO) { imageStore.deleteAll(images) }
    }

    fun saveProvider(provider: ProviderConfig) {
        val current = _state.value.settings
        val previous = current.providers.firstOrNull { it.id == provider.id }
        val localOnly = !_state.value.isLoggedIn || provider.localOnly
        val options = modelOptionsOf(provider.model)
            .ifEmpty { provider.models.map { it.id.trim() }.filter { it.isNotBlank() }.distinct() }
        val selectedModel = provider.selectedModel.trim()
            .takeIf { it in options }
            ?: previous?.selectedModel?.trim()?.takeIf { it in options }
            ?: options.firstOrNull().orEmpty()
        val finalProvider = provider.copy(
            name = provider.name.trim().ifBlank { "未命名服务商" },
            baseUrl = provider.baseUrl.trim(),
            model = options.joinToString(","),
            models = options.map { id ->
                provider.models.firstOrNull { it.id == id }
                    ?: previous?.models?.firstOrNull { it.id == id }
                    ?: ProviderModel(id = id, name = id)
            },
            selectedModel = selectedModel,
            apiKey = normalizeApiKey(provider.apiKey),
            localOnly = localOnly,
            cloudSynced = false,
        )
        val finalProviders = if (current.providers.any { it.id == finalProvider.id }) {
            current.providers.map { if (it.id == finalProvider.id) finalProvider else it }
        } else {
            current.providers + finalProvider
        }
        val activeId = current.activeProviderId?.takeIf { id -> finalProviders.any { it.id == id } }
            ?: finalProviders.firstOrNull()?.id
        val pendingDeletes = current.pendingCloudProviderDeletes
            .filterNot { it == finalProvider.id }
            .toMutableList()
        if (finalProvider.localOnly && previous?.localOnly == false) {
            pendingDeletes += finalProvider.id
        }
        val settings = current.copy(
            providers = finalProviders,
            activeProviderId = activeId,
            pendingCloudProviderDeletes = pendingDeletes.distinct(),
        )
        _state.update { it.copy(settings = settings) }
        persistSettings()
        if (normalizeApiKey(provider.apiKey) != provider.apiKey.trim()) {
            notice("已自动清理 API Key 中的非 ASCII 字符")
        }

        val account = settings.cloudAccount ?: return
        if (!finalProvider.localOnly) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    cloudApi.putProvider(account, finalProvider.toDto())
                    markProviderSynced(finalProvider.id)
                } catch (e: CloudAuthException) {
                    handleCloudAuthExpired()
                } catch (e: Throwable) {
                    Log.w(TAG, "Provider cloud sync failed", e)
                    notice("服务商已保存在本机，云端同步暂时失败")
                }
            }
        } else if (previous?.localOnly == false) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    cloudApi.deleteProvider(account, finalProvider.id)
                    clearPendingProviderDelete(finalProvider.id)
                } catch (e: CloudAuthException) {
                    handleCloudAuthExpired()
                } catch (e: Throwable) {
                    if (e is CloudApiException && e.httpStatus == 404) {
                        clearPendingProviderDelete(finalProvider.id)
                    } else {
                        Log.w(TAG, "Provider cloud delete failed", e)
                    }
                }
            }
        }
    }

    private fun markProviderSynced(providerId: String) {
        var changed = false
        val providers = _state.value.settings.providers.map {
            if (it.id == providerId && !it.localOnly && !it.cloudSynced) {
                changed = true
                it.copy(cloudSynced = true)
            } else it
        }
        if (changed) {
            _state.update { it.copy(settings = it.settings.copy(providers = providers)) }
            persistSettings()
        }
    }

    private fun clearPendingProviderDelete(providerId: String) {
        val current = _state.value.settings
        if (providerId !in current.pendingCloudProviderDeletes) return
        _state.update {
            it.copy(settings = it.settings.copy(
                pendingCloudProviderDeletes = it.settings.pendingCloudProviderDeletes.filterNot { id -> id == providerId }
            ))
        }
        persistSettings()
    }

    fun setActiveProvider(providerId: String) {
        val current = _state.value.settings
        if (current.providers.none { it.id == providerId }) return
        _state.update {
            it.copy(settings = it.settings.copy(activeProviderId = providerId))
        }
        persistSettings()
    }

    fun deleteProvider(providerId: String) {
        val current = _state.value.settings
        val removed = current.providers.firstOrNull { it.id == providerId } ?: return
        val providers = current.providers.filterNot { it.id == providerId }
        val activeId = if (current.activeProviderId == providerId) {
            providers.firstOrNull()?.id
        } else current.activeProviderId
        val pendingDeletes = current.pendingCloudProviderDeletes.toMutableList()
        if (!removed.localOnly) pendingDeletes += providerId
        _state.update {
            it.copy(settings = it.settings.copy(
                providers = providers,
                activeProviderId = activeId,
                pendingCloudProviderDeletes = pendingDeletes.distinct(),
            ))
        }
        persistSettings()
        val account = current.cloudAccount
        if (account != null && !removed.localOnly) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    cloudApi.deleteProvider(account, providerId)
                    clearPendingProviderDelete(providerId)
                } catch (e: CloudAuthException) {
                    handleCloudAuthExpired()
                } catch (e: Throwable) {
                    if (e is CloudApiException && e.httpStatus == 404) {
                        clearPendingProviderDelete(providerId)
                    } else {
                        Log.w(TAG, "Provider cloud delete failed", e)
                        notice("服务商已从本机删除，云端删除暂未完成")
                    }
                }
            }
        }
    }

    fun login(serverUrl: String, username: String, password: String) {
        beginAuth(serverUrl, username, password, register = false)
    }

    fun register(serverUrl: String, username: String, password: String) {
        beginAuth(serverUrl, username, password, register = true)
    }

    fun toggleWebSearch() {
        if ((_state.value.account?.level ?: 1) < 2) {
            notice("联网搜索需要 LV2")
            return
        }
        _state.update { it.copy(webSearch = !it.webSearch) }
    }

    fun clearAuthError() {
        _state.update { it.copy(authError = null) }
    }

    private fun beginAuth(serverUrl: String, username: String, password: String, register: Boolean) {
        if (authJob?.isActive == true) return
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(authError = "请输入账号和密码") }
            return
        }
        val normalized = runCatching { CloudEndpoint.normalize(serverUrl) }.getOrElse {
            _state.update { state -> state.copy(authError = it.message ?: "服务器地址格式不正确") }
            return
        }
        _state.update {
            it.copy(
                authBusy = true,
                authError = null,
                cloudError = null,
                settings = it.settings.copy(cloudServerUrl = normalized),
            )
        }
        persistSettings()
        authJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (register) cloudApi.register(normalized, username.trim(), password)
                    else cloudApi.login(normalized, username.trim(), password)
                }
                val account = CloudAccount(
                    username = result.user.username,
                    serverUrl = normalized,
                    token = result.token,
                    expiresAt = result.expiresAt,
                    level = result.user.level,
                )
                _state.update {
                    it.copy(
                        settings = it.settings.copy(
                            cloudAccount = account,
                            cloudServerUrl = normalized,
                        ),
                        authBusy = false,
                        authError = null,
                        cloudError = null,
                        newChatCloud = it.activeConversationId == null,
                    )
                }
                persistSettings()
                notice(if (register) "注册成功，已登录" else "登录成功")
                syncCloudData(showNoticeOnError = true)
                startCloudEvents()
                refreshProviderModels()
            } catch (e: Throwable) {
                _state.update {
                    it.copy(authBusy = false, authError = friendlyCloudError(e))
                }
            } finally {
                authJob = null
            }
        }
    }

    fun logout() {
        val account = _state.value.account ?: return
        clearLocalCloudSession("已退出登录")
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { cloudApi.logout(account) }
        }
    }

    fun syncCloudNow() {
        if (!_state.value.isLoggedIn) {
            notice("请先登录账号")
            return
        }
        syncCloudData(showNoticeOnError = true)
    }

    fun checkForUpdate() {
        if (updateCheckInFlight) return
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheckAt < 30_000L) return
        updateCheckInFlight = true
        lastUpdateCheckAt = now
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val update = runCatching { UpdateChecker.fetch(BuildConfig.VERSION_NAME) }.getOrNull()
                if (update != null && _state.value.update == null) {
                    _state.update {
                        it.copy(update = update, updateDownload = UpdateDownloadState.Idle)
                    }
                }
            } finally {
                updateCheckInFlight = false
            }
        }
    }

    fun dismissUpdate() {
        if (_state.value.update?.mandatory == true) return
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _state.update {
            it.copy(update = null, updateDownload = UpdateDownloadState.Idle)
        }
    }

    fun downloadUpdate(update: AppUpdate) {
        if (updateDownloadJob?.isActive == true) return
        _state.update {
            it.copy(updateDownload = UpdateDownloadState.Downloading(0f, 0L, -1L))
        }
        updateDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                val safeVersion = update.latestVersion.replace(Regex("[^0-9A-Za-z._-]"), "_")
                val target = File(dir, "ReinChat-$safeVersion.apk")
                tempFile = File(dir, target.name + ".part").apply { delete() }
                val request = Request.Builder()
                    .url(update.downloadUrl)
                    .header("User-Agent", "ReinChat-Android/${BuildConfig.VERSION_NAME}")
                    .build()
                updateHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("no body")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var downloaded = 0L
                            var lastUi = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastUi > 80) {
                                    val progress = if (total > 0) {
                                        (downloaded.toFloat() / total).coerceIn(0f, 1f)
                                    } else 0f
                                    _state.update {
                                        it.copy(
                                            updateDownload = UpdateDownloadState.Downloading(
                                                progress = progress,
                                                downloadedBytes = downloaded,
                                                totalBytes = total,
                                            )
                                        )
                                    }
                                    lastUi = now
                                }
                            }
                        }
                    }
                }
                if (!tempFile.exists() || tempFile.length() <= 0L) {
                    throw IllegalStateException("下载文件为空")
                }
                if (target.exists()) target.delete()
                if (!tempFile.renameTo(target)) {
                    tempFile.copyTo(target, overwrite = true)
                    tempFile.delete()
                }
                _state.update {
                    it.copy(updateDownload = UpdateDownloadState.Ready(target.absolutePath))
                }
            } catch (cancel: CancellationException) {
                tempFile?.delete()
                throw cancel
            } catch (e: Throwable) {
                tempFile?.delete()
                Log.w(TAG, "Update download failed", e)
                _state.update {
                    it.copy(
                        updateDownload = UpdateDownloadState.Failed(
                            "下载失败，请检查网络后重试"
                        )
                    )
                }
            } finally {
                updateDownloadJob = null
            }
        }
    }

    fun onAppForeground() {
        checkForUpdate()
        if (!_state.value.isLoggedIn) return
        _state.value.account?.let { account ->
            viewModelScope.launch(Dispatchers.IO) { refreshAccountLevel(account) }
        }
        startCloudEvents()
        refreshProviderModels()
        if (System.currentTimeMillis() - lastCloudSyncAt < 20_000L) return
        syncCloudData(showNoticeOnError = false)
    }

    private fun startCloudEvents() {
        if (cloudEventsJob?.isActive == true) return
        val first = _state.value.account ?: return
        cloudEventsJob = viewModelScope.launch(Dispatchers.IO) {
            var retryDelay = 2_000L
            while (isActive) {
                val account = _state.value.account ?: break
                if (account.token != first.token) break
                try {
                    cloudApi.events(account).collect { event ->
                        handleCloudSyncEvent(account, event)
                    }
                    retryDelay = 2_000L
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (auth: CloudAuthException) {
                    handleCloudAuthExpired()
                    break
                } catch (e: Throwable) {
                    Log.w(TAG, "Cloud event stream failed", e)
                }
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
            }
        }
    }

    private suspend fun refreshAccountLevel(account: CloudAccount) {
        try {
            val user = cloudApi.me(account)
            val current = _state.value.settings.cloudAccount ?: return
            if (current.token != account.token) return
            if (current.level == user.level) return
            val updated = current.copy(level = user.level)
            _state.update { it.copy(settings = it.settings.copy(cloudAccount = updated)) }
            persistSettings()
            notice(if (user.level >= 2) "账号已升级到 LV2，AI 请求将走官方中转" else "账号等级已更新为 LV${user.level}")
        } catch (auth: CloudAuthException) {
            handleCloudAuthExpired()
        } catch (e: Throwable) {
            Log.w(TAG, "Refresh account level failed", e)
        }
    }

    private fun handleCloudSyncEvent(account: CloudAccount, event: CloudSyncEventDto) {
        if (_state.value.account?.token != account.token) return
        if (event.type == "account.updated") {
            viewModelScope.launch(Dispatchers.IO) { refreshAccountLevel(account) }
            return
        }
        val activeId = _state.value.activeConversationId
        if (event.deleted) {
            syncCloudData(showNoticeOnError = false)
            return
        }
        if (event.conversationId.isNotBlank() &&
            event.conversationId == activeId &&
            _state.value.streaming?.conversationId != activeId
        ) {
            refreshActiveCloudConversation(activeId)
            return
        }
        if (event.type == "conversation.created" ||
            System.currentTimeMillis() - lastCloudSyncAt > 10_000L
        ) {
            syncCloudData(showNoticeOnError = false)
        }
    }

    private fun syncCloudData(showNoticeOnError: Boolean) {
        val account = _state.value.account ?: return
        if (fullCloudSyncJob?.isActive == true) return
        fullCloudSyncJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(cloudSyncing = true, cloudError = null) }
            try {
                cloudMutex.withLock {
                    processPendingConversationDeletes(account)
                    mergeCloudProviders(account)
                    if (_state.value.account?.token != account.token) return@withLock
                    syncRemoteConversations(account)
                }
                lastCloudSyncAt = System.currentTimeMillis()
                _state.update { it.copy(cloudSyncing = false, cloudError = null) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (auth: CloudAuthException) {
                handleCloudAuthExpired()
            } catch (e: Throwable) {
                Log.w(TAG, "Cloud sync failed", e)
                val message = friendlyCloudError(e)
                _state.update { it.copy(cloudSyncing = false, cloudError = message) }
                if (showNoticeOnError) notice("云端同步失败：$message")
            } finally {
                fullCloudSyncJob = null
            }
        }
    }

    private suspend fun mergeCloudProviders(account: CloudAccount) {
        val pendingDeletes = _state.value.settings.pendingCloudProviderDeletes.toSet().toMutableSet()
        for (id in pendingDeletes.toList()) {
            if (_state.value.account?.token != account.token) return
            try {
                cloudApi.deleteProvider(account, id)
                pendingDeletes.remove(id)
            } catch (e: CloudAuthException) {
                throw e
            } catch (e: Throwable) {
                if (e is CloudApiException && e.httpStatus == 404) {
                    pendingDeletes.remove(id)
                } else {
                    Log.w(TAG, "Provider pending delete failed: $id", e)
                }
            }
        }

        val remote = cloudApi.listProviders(account)
        if (_state.value.account?.token != account.token) return
        val current = _state.value.settings
        val localProviders = current.providers
        val remoteIds = remote.map { it.id }.toSet()
        val localById = localProviders.associateBy { it.id }.toMutableMap()
        for (provider in remote) {
            if (provider.id in pendingDeletes) continue
            val local = localById[provider.id]
            when {
                local == null -> localById[provider.id] = provider.toLocalProvider()
                local.localOnly -> Unit // the local-only choice wins
                !local.cloudSynced -> Unit // local pending edit wins until it is uploaded
                else -> {
                    val remoteLocal = provider.toLocalProvider()
                    val keptModel = local.selectedModel
                        .takeIf { it.isNotBlank() && it in remoteLocal.modelOptions }
                    localById[provider.id] = remoteLocal.copy(
                        selectedModel = keptModel ?: remoteLocal.selectedModel,
                        reasoningEffort = local.reasoningEffort,
                    )
                }
            }
        }

        // A provider that was previously synced and is now absent from the
        // server was deleted from another device. Remove the local copy too.
        localById.entries.removeAll { (id, provider) ->
            !provider.localOnly && provider.cloudSynced && id !in remoteIds
        }

        val syncedNow = mutableSetOf<String>()
        for (provider in localById.values.toList()) {
            if (provider.localOnly || provider.cloudSynced) continue
            if (_state.value.account?.token != account.token) return
            try {
                cloudApi.putProvider(account, provider.toDto())
                syncedNow += provider.id
            } catch (e: CloudAuthException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Provider upload failed: ${provider.id}", e)
            }
        }

        val merged = localById.values.map { provider ->
            if (provider.id in syncedNow) provider.copy(cloudSynced = true) else provider
        }
        val activeId = current.activeProviderId?.takeIf { id -> merged.any { it.id == id } }
            ?: merged.firstOrNull()?.id
        _state.update {
            it.copy(settings = it.settings.copy(
                providers = merged,
                activeProviderId = activeId,
                pendingCloudProviderDeletes = pendingDeletes.toList(),
            ))
        }
        persistSettings()
    }

    private suspend fun processPendingConversationDeletes(account: CloudAccount) {
        val pending = _state.value.settings.pendingCloudConversationDeletes.toSet().toMutableSet()
        if (pending.isEmpty()) return
        for (id in pending.toList()) {
            if (_state.value.account?.token != account.token) return
            try {
                cloudApi.deleteConversation(account, id)
                pending.remove(id)
            } catch (e: CloudAuthException) {
                throw e
            } catch (e: Throwable) {
                if (e is CloudApiException && e.httpStatus == 404) {
                    pending.remove(id)
                } else {
                    Log.w(TAG, "Conversation pending delete failed: $id", e)
                }
            }
        }
        _state.update {
            it.copy(settings = it.settings.copy(
                pendingCloudConversationDeletes = pending.toList()
            ))
        }
        persistSettings()
    }

    private suspend fun syncRemoteConversations(account: CloudAccount) {
        val summaries = cloudApi.listConversations(account)
        if (_state.value.account?.token != account.token) return
        val summaryById = summaries.associateBy { it.id }

        // A cloud conversation that exists locally with a server version but is
        // missing from the server was deleted on another device. Cloud is the
        // source of truth for cloud conversations, so remove the stale copy.
        val stale = _state.value.conversations.filter {
            it.cloud && it.cloudVersion > 0 && it.id !in summaryById &&
                _state.value.streaming?.conversationId != it.id
        }
        if (stale.isNotEmpty()) {
            val staleIds = stale.map { it.id }.toSet()
            val remaining = _state.value.conversations.filterNot { it.id in staleIds }
            _state.update {
                it.copy(
                    conversations = remaining,
                    activeConversationId = it.activeConversationId?.takeIf { id -> remaining.any { c -> c.id == id } }
                        ?: remaining.firstOrNull()?.id,
                )
            }
            persistConversations()
            stale.flatMap { conv -> conv.messages.flatMap { it.images } }
                .takeIf { it.isNotEmpty() }
                ?.let { images -> viewModelScope.launch(Dispatchers.IO) { imageStore.deleteAll(images) } }
        }

        for (summary in summaries) {
            if (_state.value.account?.token != account.token) return
            if (_state.value.streaming?.conversationId == summary.id) continue
            val local = _state.value.conversations.firstOrNull { it.id == summary.id }
            if (local == null || local.cloudVersion < summary.version) {
                val remote = cloudApi.getConversation(account, summary.id)
                if (_state.value.account?.token != account.token) return
                applyRemoteConversation(account, remote)
            }
        }

        // Push local cloud conversations that do not exist remotely yet, or
        // that contain local edits the server has not seen.
        val localCloud = _state.value.conversations.filter { it.cloud && it.messages.isNotEmpty() }
        for (conversation in localCloud) {
            if (_state.value.account?.token != account.token) return
            if (_state.value.streaming?.conversationId == conversation.id) continue
            val remoteSummary = summaryById[conversation.id]
            val needsUpload = remoteSummary == null ||
                conversation.cloudVersion > remoteSummary.version ||
                (conversation.cloudVersion == remoteSummary.version &&
                    conversation.updatedAt > remoteSummary.updatedAt + 50)
            if (needsUpload) {
                uploadCloudConversationLocked(account, conversation.id)
            }
        }
    }

    private suspend fun applyRemoteConversation(account: CloudAccount, remote: ConversationDto) {
        val converted = convertRemoteConversation(account, remote)
        val local = _state.value.conversations.firstOrNull { it.id == converted.id }
        if (local != null && local.cloudVersion > converted.cloudVersion) return
        if (_state.value.streaming?.conversationId == converted.id) return
        replaceConversationInState(converted)
    }

    private suspend fun convertRemoteConversation(
        account: CloudAccount,
        remote: ConversationDto,
    ): Conversation {
        val messages = remote.messages.map { message ->
            val images = message.images.mapNotNull { image ->
                val remoteId = image.remoteId ?: return@mapNotNull null
                val mime = image.mimeType.ifBlank { "image/jpeg" }
                val extension = when {
                    mime.contains("png") -> "png"
                    mime.contains("webp") -> "webp"
                    else -> "jpg"
                }
                val target = File(imagesDir, "$remoteId.$extension")
                if (!target.isFile) {
                    cloudApi.downloadImage(account, remoteId, target)
                }
                ChatImage(
                    id = image.id.ifBlank { newId() },
                    path = target.absolutePath,
                    mimeType = mime,
                    width = image.width,
                    height = image.height,
                    remoteId = remoteId,
                )
            }
            ChatMessage(
                id = message.id.ifBlank { newId() },
                role = message.roleEnum(),
                content = message.content,
                reasoning = message.reasoning,
                model = message.model,
                images = images,
                pdfName = message.pdfName,
                pdfText = message.pdfText,
                segments = message.segments.map { it.toLocalSegment() },
                createdAt = message.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                status = message.statusEnum(),
                error = message.error,
            )
        }
        return Conversation(
            id = remote.id,
            title = remote.title.ifBlank { "云端对话" },
            createdAt = remote.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            updatedAt = remote.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            messages = messages,
            cloud = true,
            cloudVersion = remote.version,
        )
    }

    private fun replaceConversationInState(conversation: Conversation) {
        val conversations = _state.value.conversations
            .filterNot { it.id == conversation.id }
            .plus(conversation)
            .sortedByDescending { it.updatedAt }
        _state.update { it.copy(conversations = conversations) }
        persistConversations()
    }

    private fun scheduleCloudConversationSync(conversationId: String) {
        val account = _state.value.account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cloudMutex.withLock {
                    if (_state.value.account?.token != account.token) return@withLock
                    uploadCloudConversationLocked(account, conversationId)
                }
            } catch (auth: CloudAuthException) {
                handleCloudAuthExpired()
            } catch (e: Throwable) {
                Log.w(TAG, "Conversation cloud sync failed", e)
                _state.update { it.copy(cloudError = friendlyCloudError(e)) }
            }
        }
    }

    private suspend fun uploadCloudConversationLocked(account: CloudAccount, conversationId: String) {
        val current = _state.value.conversations.firstOrNull { it.id == conversationId } ?: return
        if (!current.cloud || current.messages.isEmpty()) return
        if (_state.value.account?.token != account.token) return

        val prepared = ensureCloudImages(account, current)
        val latest = _state.value.conversations.firstOrNull { it.id == conversationId } ?: prepared
        val upload = latest.copy(messages = latest.messages.filterNot { it.status == MessageStatus.STREAMING })
        if (upload.messages.isEmpty()) return
        try {
            val remote = if (latest.cloudVersion <= 0) {
                cloudApi.createConversation(account, upload)
            } else {
                cloudApi.updateConversation(account, upload, latest.cloudVersion)
            }
            applyCloudVersion(conversationId, remote.version)
        } catch (conflict: CloudConflictException) {
            Log.w(TAG, "Cloud conversation version conflict", conflict)
            if (_state.value.streaming?.conversationId != conversationId) {
                applyRemoteConversation(account, conflict.remoteConversation)
                notice("云端已有更新，已拉取最新记录")
            }
        } catch (e: CloudApiException) {
            if (e.code == "conversation_exists" && latest.cloudVersion <= 0) {
                val remote = cloudApi.getConversation(account, conversationId)
                if (_state.value.streaming?.conversationId != conversationId) {
                    applyRemoteConversation(account, remote)
                }
            } else {
                throw e
            }
        }
    }

    private suspend fun ensureCloudImages(account: CloudAccount, conversation: Conversation): Conversation {
        var changed = false
        val messages = conversation.messages.map { message ->
            val images = message.images.map { image ->
                if (image.remoteId != null) image
                else {
                    val uploaded = cloudApi.uploadImage(account, image)
                    changed = true
                    image.copy(remoteId = uploaded.id, mimeType = uploaded.mimeType.ifBlank { image.mimeType })
                }
            }
            if (images != message.images) message.copy(images = images) else message
        }
        if (!changed) return conversation
        val updated = conversation.copy(messages = messages)
        replaceConversationInState(updated)
        return updated
    }

    private fun applyCloudVersion(conversationId: String, version: Long) {
        var changed = false
        val conversations = _state.value.conversations.map { conversation ->
            if (conversation.id == conversationId && conversation.cloudVersion != version) {
                changed = true
                conversation.copy(cloudVersion = version)
            } else conversation
        }
        if (changed) {
            _state.update { it.copy(conversations = conversations) }
            persistConversations()
        }
    }

    private fun refreshActiveCloudConversation(conversationId: String) {
        val account = _state.value.account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cloudMutex.withLock {
                    if (_state.value.account?.token != account.token) return@withLock
                    val local = _state.value.conversations.firstOrNull { it.id == conversationId }
                    if (local?.cloud != true || _state.value.activeConversationId != conversationId) {
                        return@withLock
                    }
                    if (_state.value.streaming?.conversationId == conversationId) return@withLock
                    val remote = cloudApi.getConversation(account, conversationId)
                    if (remote.version > local.cloudVersion) {
                        applyRemoteConversation(account, remote)
                    }
                }
            } catch (auth: CloudAuthException) {
                handleCloudAuthExpired()
            } catch (e: Throwable) {
                Log.w(TAG, "Refresh cloud conversation failed", e)
            }
        }
    }

    private fun deleteCloudConversation(conversationId: String) {
        val account = _state.value.account ?: return
        val conversation = _state.value.conversations.firstOrNull { it.id == conversationId } ?: return
        if (conversation.cloudVersion <= 0) {
            clearPendingConversationDelete(conversationId)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cloudApi.deleteConversation(account, conversationId)
                clearPendingConversationDelete(conversationId)
            } catch (auth: CloudAuthException) {
                handleCloudAuthExpired()
            } catch (e: Throwable) {
                if (e is CloudApiException && e.httpStatus == 404) {
                    clearPendingConversationDelete(conversationId)
                } else {
                    Log.w(TAG, "Cloud conversation delete failed", e)
                    notice("会话已从本机删除，云端删除暂未完成")
                }
            }
        }
    }

    private fun clearPendingConversationDelete(conversationId: String) {
        val current = _state.value.settings
        if (conversationId !in current.pendingCloudConversationDeletes) return
        _state.update {
            it.copy(settings = it.settings.copy(
                pendingCloudConversationDeletes =
                it.settings.pendingCloudConversationDeletes.filterNot { id -> id == conversationId }
            ))
        }
        persistSettings()
    }

    private fun handleCloudAuthExpired() {
        if (_state.value.account == null) return
        clearLocalCloudSession("登录已过期，请重新登录")
    }

    private fun clearLocalCloudSession(message: String) {
        cloudEventsJob?.cancel()
        cloudEventsJob = null
        val state = _state.value
        val removedCloud = state.conversations.filter { it.cloud }
        if (state.streaming?.conversationId?.let { id -> removedCloud.any { it.id == id } } == true) {
            streamJob?.cancel()
        }
        val remaining = state.conversations.filterNot { it.cloud }
        val remainingProviders = state.settings.providers.filter { it.localOnly }
        val nextActiveConversation = if (state.conversations.firstOrNull { it.id == state.activeConversationId }?.cloud == true) {
            remaining.firstOrNull()?.id
        } else state.activeConversationId
        val nextActiveProvider = state.settings.activeProviderId
            ?.takeIf { id -> remainingProviders.any { it.id == id } }
            ?: remainingProviders.firstOrNull()?.id
        _state.update {
            it.copy(
                conversations = remaining,
                activeConversationId = nextActiveConversation,
                settings = it.settings.copy(
                    cloudAccount = null,
                    providers = remainingProviders,
                    activeProviderId = nextActiveProvider,
                ),
                streaming = null,
                newChatCloud = false,
                cloudSyncing = false,
                cloudError = null,
                authBusy = false,
                authError = null,
                draft = "",
                attachments = emptyList(),
                pdfAttachment = null,
                pdfBusy = false,
                pdfError = null,
                editingMessageId = null,
            )
        }
        lastCloudSyncAt = 0L
        rewindUndo = null
        persistConversations()
        persistSettings()
        val images = removedCloud.flatMap { conv -> conv.messages.flatMap { it.images } }
        if (images.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) { imageStore.deleteAll(images) }
        }
        notice(message)
    }

    private fun friendlyCloudError(error: Throwable): String = when (error) {
        is CloudAuthException -> error.message ?: "登录已过期，请重新登录"
        is CloudApiException -> error.message ?: "云端请求失败"
        is IllegalArgumentException -> error.message ?: "参数不正确"
        else -> friendlyNetworkError(error)
    }

    fun persistNow() {
        persistConversations()
        persistSettings()
    }

    private fun persistConversations() {
        conversationSaves.trySend(_state.value.conversations)
    }

    private fun persistSettings() {
        // Settings changes are rare and small. Persist them synchronously so a
        // provider cannot be lost when the user immediately leaves or swipes
        // the app away.
        val saved = runBlocking(Dispatchers.IO) {
            settingsStore.save(_state.value.settings)
        }
        if (!saved) {
            notice("服务商配置保存失败，请重试")
        }
    }

    private fun notice(message: String) {
        _events.trySend(ChatEvent.Notice(message))
    }

    override fun onCleared() {
        streamJob?.cancel()
        importJob?.cancel()
        pdfJob?.cancel()
        titleJob?.cancel()
        ChatStreamService.stop(getApplication())
        super.onCleared()
    }
}
