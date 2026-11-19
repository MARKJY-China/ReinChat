package com.reinchat.app.net

import com.reinchat.app.data.ChatImage
import com.reinchat.app.data.ChatMessage
import com.reinchat.app.data.MessageRole
import com.reinchat.app.data.MessageSegment
import com.reinchat.app.data.MessageStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudMessageWireTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun assistantMessage(): ChatMessage = ChatMessage(
        id = "assistant-1",
        role = MessageRole.ASSISTANT,
        content = "最终回答",
        reasoning = "思考 A思考 B",
        model = "demo-model",
        pdfName = "扫描件.pdf",
        pdfText = "OCR 识别出来的正文",
        segments = listOf(
            MessageSegment.reasoning("思考 A"),
            MessageSegment.tool(
                name = "web_search",
                status = MessageSegment.TOOL_STATUS_DONE,
                detail = "搜索结果",
            ),
            MessageSegment.reasoning("思考 B"),
        ),
        status = MessageStatus.CANCELLED,
    )

    @Test
    fun relayPayloadCarriesPdfTextAndSegments() {
        val dto = relayMessageDto(assistantMessage())
        assertEquals("扫描件.pdf", dto.pdfName)
        assertEquals("OCR 识别出来的正文", dto.pdfText)
        assertEquals(3, dto.segments.size)
        assertEquals("web_search", dto.segments[1].name)
        assertEquals(MessageSegment.TOOL_STATUS_DONE, dto.segments[1].status)
        assertEquals("STOPPED", dto.status)

        val encoded = json.encodeToString(dto)
        assertTrue(encoded.contains("\"pdfText\":\"OCR 识别出来的正文\""))
        assertTrue(encoded.contains("\"pdfName\":\"扫描件.pdf\""))
        assertTrue(encoded.contains("\"segments\""))
    }

    @Test
    fun wireRoundTripPreservesSegmentsAndPdfFields() {
        val decoded = json.decodeFromString<CloudMessageDto>(
            json.encodeToString(relayMessageDto(assistantMessage()))
        )
        assertEquals("最终回答", decoded.content)
        assertEquals("OCR 识别出来的正文", decoded.pdfText)
        assertEquals(3, decoded.segments.size)

        val segments = decoded.segments.map { it.toLocalSegment() }
        assertEquals(MessageSegment.TYPE_REASONING, segments[0].type)
        assertEquals("思考 A", segments[0].text)
        assertEquals("web_search", segments[1].name)
        assertEquals(MessageSegment.TOOL_STATUS_DONE, segments[1].status)
        assertEquals(MessageSegment.TYPE_REASONING, segments[2].type)
        assertEquals("思考 B", segments[2].text)
        // The server stores STOPPED for a cancelled message; the local model
        // treats both STOPPED and CANCELLED as "user stopped".
        assertEquals(MessageStatus.STOPPED, decoded.statusEnum())
        assertTrue(decoded.statusEnum().isCancelled)
        assertTrue(decoded.statusEnum().isTerminal)
    }

    @Test
    fun segmentWithoutStatusDecodesAsRunning() {
        val dto = json.decodeFromString<CloudMessageDto>(
            """{"id":"m","role":"assistant","segments":[{"type":"tool","name":"web_search"}]}"""
        )
        assertEquals(MessageSegment.TOOL_STATUS_RUNNING, dto.segments.single().toLocalSegment().status)
    }

    @Test
    fun imageIdsDifferBetweenSyncAndRelay() {
        val image = ChatImage(
            id = "local-image",
            path = "/tmp/a.jpg",
            remoteId = "cloud-image",
            width = 10,
            height = 20,
        )
        val message = ChatMessage(role = MessageRole.USER, content = "看图", images = listOf(image))

        val sync = messageDto(message).images.single()
        assertEquals("local-image", sync.id)
        assertEquals("cloud-image", sync.remoteId)

        val relay = relayMessageDto(message).images.single()
        assertEquals("cloud-image", relay.id)
        assertEquals("cloud-image", relay.remoteId)
    }

    @Test
    fun directProviderPayloadInlinesPdfText() {
        // Mirrors ChatApiClient: the direct (non-relay) path must forward the
        // OCR text too, otherwise a scanned PDF would be ignored.
        val message = assistantMessage()
        val text = directMessageText(message)
        assertTrue(text.contains("最终回答"))
        assertTrue(text.contains("[附件 PDF：扫描件.pdf]"))
        assertTrue(text.contains("OCR 识别出来的正文"))
    }
}
