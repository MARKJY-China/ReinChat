package com.reinchat.app.data

/**
 * Where the text of a newly attached PDF comes from.
 *
 * The local extractor in [PdfTextExtractor] is dependency-free and only handles
 * PDFs with a usable text layer. Scanned documents (and CID/identity-H text
 * without a ToUnicode map) produce no text, so the composer either falls back to
 * the authenticated server OCR endpoint (`/api/v1/pdf/ocr`) or, when the user is
 * not signed in, keeps the attachment and explains why OCR is unavailable.
 */
enum class PdfTextSource {
    /** The local extractor found usable text; no network call is made. */
    LOCAL_TEXT,

    /** No local text and a cloud account is signed in: call the OCR endpoint. */
    SERVER_OCR,

    /** No local text and nobody is signed in: keep the PDF, explain login. */
    LOGIN_REQUIRED,
}

/** Result of [PdfTextPolicy.decide] for one attached PDF. */
data class PdfTextDecision(
    val source: PdfTextSource,
    /** Composer state while the text is being resolved. */
    val pendingState: PdfAttachmentState,
    /** Persistent explanation shown next to the attachment; null when local text exists. */
    val message: String?,
) {
    val needsServerOcr: Boolean
        get() = source == PdfTextSource.SERVER_OCR

    val blockedByLogin: Boolean
        get() = source == PdfTextSource.LOGIN_REQUIRED
}

/**
 * Pure decision table for the PDF OCR fallback. Kept out of the ViewModel so the
 * fallback can be unit-tested and exercised by the debug OCR probe without a
 * device or a live server.
 */
object PdfTextPolicy {

    const val OCR_PROGRESS_MESSAGE = "未检测到文字层，正在云端 OCR 识别…"
    const val LOGIN_REQUIRED_MESSAGE =
        "未检测到文字层。扫描件需要登录后使用云端 OCR；附件仍会随消息发送。"
    const val OCR_EMPTY_MESSAGE = "云端 OCR 没有识别出文字，附件仍会随消息发送。"

    /** Message for a failed OCR request; the attachment is kept either way. */
    fun ocrFailedMessage(reason: String?): String {
        val detail = reason?.trim().orEmpty()
        return if (detail.isBlank()) {
            "云端 OCR 失败，附件仍会随消息发送。"
        } else {
            "云端 OCR 失败：$detail；附件仍会随消息发送。"
        }
    }

    fun decide(localText: String, loggedIn: Boolean): PdfTextDecision = when {
        localText.isNotBlank() -> PdfTextDecision(
            source = PdfTextSource.LOCAL_TEXT,
            pendingState = PdfAttachmentState.READY,
            message = null,
        )

        loggedIn -> PdfTextDecision(
            source = PdfTextSource.SERVER_OCR,
            pendingState = PdfAttachmentState.OCR_RUNNING,
            message = OCR_PROGRESS_MESSAGE,
        )

        else -> PdfTextDecision(
            source = PdfTextSource.LOGIN_REQUIRED,
            pendingState = PdfAttachmentState.EMPTY,
            message = LOGIN_REQUIRED_MESSAGE,
        )
    }
}
