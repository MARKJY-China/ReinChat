package com.reinchat.app.net

import com.reinchat.app.data.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ApiEndpointTest {

    @Test
    fun `appends responses path to versioned base url`() {
        assertEquals(
            "https://api.openai.com/v1/responses",
            ApiEndpoint.endpointUrl("https://api.openai.com/v1", ApiProtocol.RESPONSES),
        )
    }

    @Test
    fun `does not duplicate full endpoint`() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            ApiEndpoint.endpointUrl(
                "https://api.example.com/v1/chat/completions/",
                ApiProtocol.CHAT_COMPLETIONS,
            ),
        )
    }

    @Test
    fun `adds https when scheme is omitted`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            ApiEndpoint.endpointUrl("api.deepseek.com/v1", ApiProtocol.CHAT_COMPLETIONS),
        )
    }

    @Test
    fun `accepts localhost http endpoints`() {
        assertEquals(
            "http://10.0.2.2:8000/v1/responses",
            ApiEndpoint.endpointUrl("http://10.0.2.2:8000/v1", ApiProtocol.RESPONSES),
        )
    }

    @Test
    fun `rejects malformed urls`() {
        assertNotNull(ApiEndpoint.validationError(""))
        assertNotNull(ApiEndpoint.validationError("ftp://example.com"))
        assertNull(ApiEndpoint.validationError("example.com/v1"))
    }
}
