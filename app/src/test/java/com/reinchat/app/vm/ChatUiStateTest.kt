package com.reinchat.app.vm

import com.reinchat.app.data.AppSettings
import com.reinchat.app.data.ChatImage
import com.reinchat.app.data.PdfAttachment
import com.reinchat.app.data.PdfAttachmentState
import com.reinchat.app.data.PendingAttachmentKind
import com.reinchat.app.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {

    @Test
    fun idleWhenNothingIsStreaming() {
        val state = ChatUiState()
        assertEquals(StreamPhase.IDLE, state.streamPhase)
        assertFalse(state.isWaitingForFirstToken)
        assertFalse(state.isStreaming)
    }

    @Test
    fun waitingBeforeFirstTokenAndStreamingAfter() {
        val waiting = ChatUiState(
            streaming = StreamingSession(
                conversationId = "c",
                messageId = "m",
                startedAt = 0L,
                phase = StreamPhase.WAITING,
            )
        )
        assertEquals(StreamPhase.WAITING, waiting.streamPhase)
        assertTrue(waiting.isWaitingForFirstToken)
        assertTrue(waiting.isStreaming)

        val streaming = waiting.copy(
            streaming = waiting.streaming?.copy(phase = StreamPhase.STREAMING)
        )
        assertEquals(StreamPhase.STREAMING, streaming.streamPhase)
        assertFalse(streaming.isWaitingForFirstToken)
    }

    @Test
    fun pendingAttachmentsMergeImagesAndPdf() {
        val pdf = PdfAttachment(
            name = "report.pdf",
            text = "hello",
            state = PdfAttachmentState.READY,
        )
        val state = ChatUiState(
            attachments = listOf(
                ChatImage(
                    path = "/tmp/a.jpg",
                    mimeType = "image/jpeg",
                    width = 10,
                    height = 20,
                    sourceUri = "content://image/1",
                )
            ),
            pdfAttachment = pdf,
        )
        val pending = state.pendingAttachments
        assertEquals(2, pending.size)
        assertEquals(PendingAttachmentKind.IMAGE, pending[0].kind)
        assertEquals("content://image/1", pending[0].uri)
        assertEquals(PendingAttachmentKind.PDF, pending[1].kind)
        assertEquals("report.pdf", pending[1].name)
        assertEquals("hello", pending[1].text)
    }

    @Test
    fun pdfAloneMakesTheMessageSendableButNotWhileStreaming() {
        val pdf = PdfAttachment(
            name = "a.pdf",
            text = "text",
            state = PdfAttachmentState.READY,
        )
        val idle = ChatUiState(pdfAttachment = pdf)
        assertTrue(idle.canSend)
        assertFalse(idle.isStreaming)

        val busy = idle.copy(
            streaming = StreamingSession("c", "m", 0L, StreamPhase.STREAMING)
        )
        assertFalse(busy.canSend)
    }

    @Test
    fun attachedPdfWithoutTextIsStillSendable() {
        // Signed-out OCR fallback keeps the attachment with state EMPTY.
        val pdf = PdfAttachment(
            name = "scan.pdf",
            text = "",
            state = PdfAttachmentState.EMPTY,
            error = "未检测到文字层。扫描件需要登录后使用云端 OCR；附件仍会随消息发送。",
        )
        val state = ChatUiState(pdfAttachment = pdf)
        assertFalse(pdf.hasText)
        assertFalse(pdf.busiest)
        assertTrue(state.canSend)
        assertEquals(PdfAttachmentState.EMPTY, state.pendingAttachments.single().state)
    }

    @Test
    fun settingsExposeScaleAndTheme() {
        val state = ChatUiState(
            settings = AppSettings(fontScale = 1.15f, themeMode = ThemeMode.DARK)
        )
        assertEquals(1.15f, state.fontScale, 0.0001f)
        assertEquals(ThemeMode.DARK, state.themeMode)
    }
}
