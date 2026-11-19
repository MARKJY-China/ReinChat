package com.reinchat.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStoreNormalizeTest {

    @Test
    fun streamingMessageWithOnlyToolSegmentsSurvivesRestart() {
        val message = ChatMessage(
            id = "assistant-tool-only",
            role = MessageRole.ASSISTANT,
            content = "",
            reasoning = "",
            segments = listOf(
                MessageSegment.tool(
                    name = "web_search",
                    status = MessageSegment.TOOL_STATUS_RUNNING,
                    detail = "query",
                )
            ),
            status = MessageStatus.STREAMING,
        )
        val normalized = normalizeStoredConversation(
            Conversation(id = "c", title = "t", messages = listOf(message))
        )
        val kept = normalized.messages.single()
        assertEquals(MessageStatus.CANCELLED, kept.status)
        assertEquals(1, kept.segments.size)
        assertEquals("web_search", kept.segments.single().name)
    }

    @Test
    fun streamingMessageWithoutAnyOutputIsDropped() {
        val normalized = normalizeStoredConversation(
            Conversation(
                id = "c",
                title = "t",
                messages = listOf(
                    ChatMessage(role = MessageRole.ASSISTANT, status = MessageStatus.STREAMING)
                ),
            )
        )
        assertTrue(normalized.messages.isEmpty())
    }

    @Test
    fun cancelledToolOnlyMessageIsKept() {
        val message = ChatMessage(
            role = MessageRole.ASSISTANT,
            status = MessageStatus.CANCELLED,
            segments = listOf(
                MessageSegment.tool(
                    name = "fetch_url",
                    status = MessageSegment.TOOL_STATUS_FAILED,
                    detail = "超时",
                )
            ),
        )
        val normalized = normalizeStoredConversation(
            Conversation(id = "c", title = "t", messages = listOf(message))
        )
        assertEquals(1, normalized.messages.size)
    }
}
