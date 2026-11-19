package com.reinchat.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.reinchat.app.data.ChatMessage
import com.reinchat.app.data.MessageRole
import com.reinchat.app.data.MessageStatus
import com.reinchat.app.data.PdfAttachmentState
import com.reinchat.app.data.PdfTextExtractor
import com.reinchat.app.data.PdfTextPolicy
import com.reinchat.app.ui.components.Composer
import com.reinchat.app.ui.components.MessageItem
import com.reinchat.app.ui.theme.ReinColors
import com.reinchat.app.ui.theme.ReinTheme
import com.reinchat.app.vm.ChatUiState
import com.reinchat.app.vm.ChatViewModel
import com.reinchat.app.vm.StreamPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Debug-only end-to-end probe for the scanned-PDF OCR fallback.
 *
 * It writes a PDF with no text layer, drives the real [ChatViewModel] through
 * `attachPdf` → server OCR (`/api/v1/pdf/ocr`) → `send` → relay, and prints each
 * decision plus the resulting segments on screen and in logcat (tag `OcrProbe`).
 *
 * Run it against a local mock server (see docs/ANDROID_OCR_TOOLS.md):
 * ```
 * adb shell am start -n com.reinchat.app.debug/com.reinchat.app.DebugSeedActivity \
 *   --ez demo_cloud true --es cloud_server http://10.0.2.2:8787
 * adb shell am start -n com.reinchat.app.debug/com.reinchat.app.DebugOcrProbeActivity
 * adb logcat -s OcrProbe
 * ```
 */
class DebugOcrProbeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "OcrProbe"
    }

    private val lines = mutableStateListOf<String>()
    private var result by mutableStateOf("运行中…")
    private var latestMessage by mutableStateOf<ChatMessage?>(null)
    private var streaming by mutableStateOf(false)
    private var phase by mutableStateOf(StreamPhase.IDLE)
    private var uiState by mutableStateOf(ChatUiState())

    private lateinit var vm: ChatViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[ChatViewModel::class.java]
        setContent {
            ReinTheme {
                ProbeScreen(
                    lines = lines.toList(),
                    result = result,
                    message = latestMessage,
                    isStreaming = streaming,
                    phase = phase,
                    state = uiState,
                )
            }
        }
        lifecycleScope.launch {
            vm.state.collect { state ->
                uiState = state
                streaming = state.isStreaming
                phase = state.streamPhase
                latestMessage = state.activeConversation?.messages
                    ?.lastOrNull { it.role == MessageRole.ASSISTANT }
                    ?: state.conversations.flatMap { it.messages }
                        .lastOrNull { it.role == MessageRole.ASSISTANT }
                val attachment = state.pdfAttachment
                if (state.pdfBusy && attachment?.state == PdfAttachmentState.OCR_RUNNING) {
                    noteOnce("busy+OCR_RUNNING 已出现")
                }
            }
        }
        lifecycleScope.launch { runProbe() }
    }

    private fun noteOnce(text: String) {
        if (lines.none { it == text }) log(text)
    }

    private fun log(text: String) {
        Log.i(TAG, text)
        lines += text
    }

    private suspend fun runProbe() {
        try {
            val settings = vm.state.value.settings
            log("account=${settings.cloudAccount?.username ?: "<未登录>"} level=${settings.cloudAccount?.level ?: 0} server=${settings.cloudAccount?.serverUrl ?: "-"}")
            log("provider=${settings.activeProvider?.id ?: "<无>"} model=${settings.activeProvider?.activeModel ?: "-"}")

            log("1) 写入无文字层 PDF（模拟扫描件）")
            val pdf = writeScannedPdf()
            val local = withContext(Dispatchers.IO) { PdfTextExtractor.extract(pdf) }
            log("   ${pdf.name} · ${pdf.length()} bytes · 本地提取=${local.text.length} 字（期望 0）")
            val decision = PdfTextPolicy.decide(local.text, settings.cloudAccount != null)
            log("   决策=${decision.source} pendingState=${decision.pendingState}")
            log("   提示=${decision.message ?: "-"}")

            log("2) ChatViewModel.attachPdf()")
            vm.attachPdf(Uri.fromFile(pdf))
            val attachedState = withTimeoutOrNull(180_000) {
                vm.state.first { state ->
                    val attachment = state.pdfAttachment
                    attachment != null &&
                        !state.pdfBusy &&
                        attachment.state != PdfAttachmentState.OCR_RUNNING &&
                        attachment.state != PdfAttachmentState.EXTRACTING &&
                        attachment.state != PdfAttachmentState.PENDING
                }
            }
            if (attachedState == null) {
                fail("等待 OCR 结果超时")
                return
            }
            val attachment = attachedState.pdfAttachment!!
            log("   状态=${attachment.state} ocrUsed=${attachment.ocrUsed} pages=${attachment.pages} 文字=${attachment.text.length} 字")
            log("   错误=${attachment.error ?: "-"}")
            log("   文本片段=${attachment.text.take(48).replace("\n", " ")}")
            log("   canSend=${attachedState.canSend}")

            if (intent.getBooleanExtra("attach_only", false)) {
                // Error path: the OCR endpoint is unreachable/failing. The PDF
                // must stay attached, explain the failure and remain sendable.
                val failedSoftly = attachment.state == PdfAttachmentState.EMPTY &&
                    attachment.error.orEmpty().contains("云端 OCR 失败") &&
                    attachedState.canSend
                result = if (failedSoftly) "PASS（OCR 失败路径）" else "FAIL（OCR 失败路径）"
                log("   OCR 失败路径：state=${attachment.state} canSend=${attachedState.canSend}")
                log("   结果=$result")
                return
            }

            if (intent.getBooleanExtra("no_cloud", false)) {
                // Signed-out path: OCR must not be attempted, the attachment has
                // to stay sendable, and the UI must explain that OCR needs login.
                val explained = attachment.state == PdfAttachmentState.EMPTY &&
                    attachment.error.orEmpty().contains("登录") &&
                    !attachment.ocrUsed &&
                    attachedState.canSend
                result = if (explained) "PASS（未登录路径）" else "FAIL（未登录路径）"
                log("   未登录：state=${attachment.state} canSend=${attachedState.canSend} ocrUsed=${attachment.ocrUsed}")
                log("   结果=$result")
                return
            }

            log("3) ChatViewModel.send() → 云端 relay")
            vm.send()
            val done = withTimeoutOrNull(90_000) {
                vm.state.first { state ->
                    if (state.isStreaming) return@first false
                    val message = state.activeConversation?.messages
                        ?.lastOrNull { it.role == MessageRole.ASSISTANT }
                    message != null && message.status.isTerminal && message.content.isNotBlank()
                }
            }
            if (done == null) {
                fail("等待 relay 回答超时")
                return
            }
            val message = done.activeConversation?.messages
                ?.lastOrNull { it.role == MessageRole.ASSISTANT }
            val segments = message?.segments.orEmpty()
            log("   正文长度=${message?.content?.length ?: 0}")
            segments.forEachIndexed { index, segment ->
                log(
                    "   segment[$index] type=${segment.type} name=${segment.name.ifBlank { "-" }} " +
                        "status=${segment.status} label=${segment.label} text=${segment.text.length} 字"
                )
            }
            val userMessage = done.activeConversation?.messages
                ?.lastOrNull { it.role == MessageRole.USER }
            log("   用户消息 pdfText=${userMessage?.pdfText?.length ?: 0} 字 pdfName=${userMessage?.pdfName ?: "-"}")

            val approved = attachment.ocrUsed &&
                attachment.text.isNotBlank() &&
                attachment.state == PdfAttachmentState.READY &&
                segments.size >= 4 &&
                segments.first().type == "reasoning" &&
                segments[1].name == "web_search" &&
                segments[3].name == "fetch_url" &&
                segments.last().type == "reasoning" &&
                message?.content?.isNotBlank() == true
            result = if (approved) "PASS" else "FAIL"
            log("结果=$result")
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Log.e(TAG, "probe crashed", t)
            fail(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun fail(reason: String) {
        result = "FAIL: $reason"
        log("结果=FAIL: $reason")
    }

    /** Minimal valid PDF with a graphics-only content stream: no text layer. */
    private fun writeScannedPdf(): File {
        val content = "0.4 w\n60 60 m 540 780 l S\n"
        val contentBytes = content.toByteArray(StandardCharsets.ISO_8859_1)
        val body = buildString {
            append("%PDF-1.4\n")
            append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
            append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
            append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R >>\nendobj\n")
            append("4 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n")
            append(content)
            append("endstream\nendobj\n")
            append("trailer\n<< /Root 1 0 R >>\n%%EOF\n")
        }
        val file = File(cacheDir, "probe-scan.pdf")
        file.writeBytes(body.toByteArray(StandardCharsets.ISO_8859_1))
        return file
    }
}

@Composable
private fun ProbeScreen(
    lines: List<String>,
    result: String,
    message: ChatMessage?,
    isStreaming: Boolean,
    phase: StreamPhase,
    state: ChatUiState,
) {
    val passed = result.startsWith("PASS")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReinColors.Deep)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        Text(
            text = "PDF OCR 回退探针",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = ReinColors.TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = result,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = when {
                passed -> ReinColors.Mint
                result.startsWith("FAIL") -> ReinColors.Danger
                else -> ReinColors.TextSecondary
            },
        )
        Spacer(Modifier.height(10.dp))
        lines.forEach { line ->
            Text(
                text = line,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = ReinColors.TextSecondary,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "消息预览（真实 MessageItem）",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = ReinColors.TextTertiary,
        )
        Spacer(Modifier.height(6.dp))
        val preview = message ?: ChatMessage(role = MessageRole.ASSISTANT, status = MessageStatus.STREAMING)
        MessageItem(
            message = preview,
            animateIn = false,
            isStreaming = isStreaming,
            streamPhase = phase,
            canRewind = false,
            actionsOpen = false,
            onToggleActions = {},
            onCloseActions = {},
            onCopy = {},
            onRetry = {},
            onEdit = {},
            onRewind = {},
            onOpenImage = {},
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "输入区预览（真实 Composer）",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = ReinColors.TextTertiary,
        )
        Spacer(Modifier.height(6.dp))
        Composer(
            draft = state.draft,
            pendingAttachments = state.pendingAttachments,
            isStreaming = isStreaming,
            importingImage = state.importingImage,
            pdfBusy = state.pdfBusy,
            pdfError = state.pdfError,
            editing = state.editingMessageId != null,
            providerLabel = state.activeProvider?.activeModel,
            webSearch = state.webSearch,
            searchAvailable = state.isLoggedIn,
            relayMode = state.isLoggedIn,
            onToggleWebSearch = {},
            canSend = state.canSend,
            onDraftChange = {},
            onAddImage = {},
            onTakePhoto = {},
            onAddFile = {},
            onAttachPdf = {},
            onPaste = {},
            onRemovePdf = {},
            onClearPdfError = {},
            onRemoveAttachment = {},
            onSend = {},
            onStop = {},
            onCancelEdit = {},
            onProviderClick = {},
            focusRequester = remember { FocusRequester() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
