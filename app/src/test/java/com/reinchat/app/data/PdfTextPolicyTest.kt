package com.reinchat.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextPolicyTest {

    @Test
    fun localTextSkipsOcr() {
        val decision = PdfTextPolicy.decide("已经提取到的文字", loggedIn = true)
        assertEquals(PdfTextSource.LOCAL_TEXT, decision.source)
        assertEquals(PdfAttachmentState.READY, decision.pendingState)
        assertNull(decision.message)
        assertFalse(decision.needsServerOcr)
        assertFalse(decision.blockedByLogin)
    }

    @Test
    fun scannedPdfFallsBackToServerOcrWhenLoggedIn() {
        val decision = PdfTextPolicy.decide("", loggedIn = true)
        assertEquals(PdfTextSource.SERVER_OCR, decision.source)
        assertEquals(PdfAttachmentState.OCR_RUNNING, decision.pendingState)
        assertTrue(decision.needsServerOcr)
        assertFalse(decision.blockedByLogin)
        assertTrue(decision.message.orEmpty().contains("云端 OCR"))
    }

    @Test
    fun scannedPdfWithoutLoginKeepsAttachmentAndExplainsLogin() {
        val decision = PdfTextPolicy.decide("   ", loggedIn = false)
        assertEquals(PdfTextSource.LOGIN_REQUIRED, decision.source)
        assertEquals(PdfAttachmentState.EMPTY, decision.pendingState)
        assertFalse(decision.needsServerOcr)
        assertTrue(decision.blockedByLogin)
        val message = decision.message.orEmpty()
        assertTrue(message.contains("登录"))
        assertTrue(message.contains("仍会随消息发送"))
    }

    @Test
    fun ocrFailureMessageKeepsAttachmentSendable() {
        val message = PdfTextPolicy.ocrFailedMessage("HTTP 502")
        assertTrue(message.contains("HTTP 502"))
        assertTrue(message.contains("附件仍会随消息发送"))
        assertTrue(PdfTextPolicy.ocrFailedMessage(null).contains("附件仍会随消息发送"))
    }
}
