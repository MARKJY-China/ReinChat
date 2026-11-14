package com.reinchat.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    @Test
    fun `dispatches multiline data on blank line`() {
        val parser = SseParser()
        assertNull(parser.feedLine("event: message"))
        assertNull(parser.feedLine("data: {\"a\":1,"))
        assertNull(parser.feedLine("data: \"b\":2}"))
        val event = parser.feedLine("")
        assertEquals("message", event?.event)
        assertEquals("{\"a\":1,\n\"b\":2}", event?.data)
    }

    @Test
    fun `tolerates crlf comments and multi line events`() {
        val parser = SseParser()
        assertNull(parser.feedLine(": keep-alive\r"))
        assertNull(parser.feedLine("data: one\r"))
        val first = parser.feedLine("\r")
        assertEquals("one", first?.data)
        assertNull(parser.feedLine("data: two"))
        val second = parser.feedLine("")
        assertEquals("two", second?.data)
    }

    @Test
    fun `flushes trailing event at end of stream`() {
        val parser = SseParser()
        parser.feedLine("data: [DONE]")
        val event = parser.finish()
        assertTrue(event != null)
        assertEquals("[DONE]", event?.data)
    }
}
