package com.reinchat.app.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the local archive format (`ConversationStore` uses the same Json
 * options). If tool segments vanish after a restart, this test shows whether the
 * model survives serialization.
 */
class ConversationSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun conversation(): Conversation = Conversation(
        id = "conversation-1",
        title = "工具时间线",
        messages = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.USER,
                content = "帮我查一下扫描件",
                pdfName = "扫描件.pdf",
                pdfText = "OCR 识别出来的正文",
            ),
            ChatMessage(
                id = "assistant-1",
                role = MessageRole.ASSISTANT,
                content = "最终回答",
                reasoning = "思考 A思考 B",
                model = "demo-model",
                segments = listOf(
                    MessageSegment.reasoning("思考 A"),
                    MessageSegment.tool(
                        name = "web_search",
                        status = MessageSegment.TOOL_STATUS_DONE,
                        detail = "搜索结果",
                        label = "搜索网页",
                    ),
                    MessageSegment.reasoning("思考 B"),
                    MessageSegment.tool(
                        name = "fetch_url",
                        status = MessageSegment.TOOL_STATUS_FAILED,
                        detail = "超时",
                        label = "浏览网页",
                    ),
                    MessageSegment.reasoning("思考 C"),
                ),
                status = MessageStatus.CANCELLED,
            ),
        ),
    )

    @Test
    fun localArchiveRoundTripKeepsToolSegments() {
        val decoded = json.decodeFromString<Conversation>(
            json.encodeToString(conversation())
        )
        val assistant = decoded.messages.last()
        assertEquals(5, assistant.segments.size)
        assertEquals(
            listOf(
                MessageSegment.TYPE_REASONING,
                MessageSegment.TYPE_TOOL,
                MessageSegment.TYPE_REASONING,
                MessageSegment.TYPE_TOOL,
                MessageSegment.TYPE_REASONING,
            ),
            assistant.segments.map { it.type },
        )
        assertEquals("思考 A", assistant.segments[0].text)
        assertEquals("搜索网页", assistant.segments[1].label)
        assertEquals(MessageSegment.TOOL_STATUS_DONE, assistant.segments[1].status)
        assertEquals("浏览网页", assistant.segments[3].label)
        assertEquals(MessageSegment.TOOL_STATUS_FAILED, assistant.segments[3].status)
        assertEquals("思考 C", assistant.segments[4].text)
        assertEquals(MessageStatus.CANCELLED, assistant.status)

        val user = decoded.messages.first()
        assertEquals("扫描件.pdf", user.pdfName)
        assertEquals("OCR 识别出来的正文", user.pdfText)
        assertTrue(user.hasPdf)
    }

    @Test
    fun legacyArchiveWithoutSegmentsStillLoads() {
        val raw = """
            {"id":"c","title":"旧会话","messages":[
              {"id":"m","role":"ASSISTANT","content":"旧回答","reasoning":"旧思考"}
            ]}
        """.trimIndent()
        val decoded = json.decodeFromString<Conversation>(raw)
        val message = decoded.messages.single()
        assertTrue(message.segments.isEmpty())
        assertEquals("旧思考", message.reasoning)
        assertFalse(message.hasPdf)
    }

    @Test
    fun segmentsSurviveTheEncodeDefaultsFlagUsedByTheStore() {
        // Defaults such as `ready`-less tool status must not be silently dropped
        // when a segment only carries the fields the server sent.
        val raw = """
            {"id":"c","title":"x","messages":[
              {"id":"m","role":"ASSISTANT","content":"答案","status":"COMPLETE",
               "segments":[{"type":"tool","name":"web_search","status":"running","detail":"q"}]}
            ]}
        """.trimIndent()
        val decoded = json.decodeFromString<Conversation>(raw)
        val segment = decoded.messages.single().segments.single()
        assertEquals(MessageSegment.TOOL_STATUS_RUNNING, segment.status)
        assertEquals("q", segment.detail)
        val reencoded = json.encodeToString(decoded)
        assertTrue(reencoded.contains("\"segments\""))
        assertTrue(reencoded.contains("web_search"))
    }
}
