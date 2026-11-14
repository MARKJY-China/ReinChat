package com.reinchat.app.net

data class ServerSentEvent(
    val event: String?,
    val data: String,
)

/**
 * Incremental SSE parser. Feed complete physical lines and dispatch whenever a
 * blank line terminates an event. Tolerates both LF and CRLF plus comments and
 * multi-line data fields.
 */
class SseParser {
    private val data = StringBuilder()
    private var event: String? = null

    fun feedLine(rawLine: String): ServerSentEvent? {
        val line = rawLine.removeSuffix("\r")
        if (line.isEmpty()) return dispatch()

        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field: String
        val value: String
        if (colon < 0) {
            field = line
            value = ""
        } else {
            field = line.substring(0, colon)
            value = line.substring(colon + 1).removePrefix(" ")
        }
        when (field) {
            "data" -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(value)
            }
            "event" -> event = value
            else -> Unit
        }
        return null
    }

    fun finish(): ServerSentEvent? = dispatch()

    private fun dispatch(): ServerSentEvent? {
        if (data.isEmpty()) {
            event = null
            return null
        }
        val result = ServerSentEvent(event = event, data = data.toString())
        data.clear()
        event = null
        return result
    }
}
