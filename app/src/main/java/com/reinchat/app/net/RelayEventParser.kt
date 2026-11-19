package com.reinchat.app.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A single decoded relay SSE payload. Kept separate from the OkHttp read loop so
 * snapshot replay and tool state transitions are unit-testable.
 */
internal sealed interface RelayFrame {
    /** Events to forward; [terminal] means the job also reported a terminal status. */
    data class Events(val events: List<StreamEvent>, val terminal: Boolean = false) : RelayFrame

    /** The job finished successfully; the stream can be closed. */
    data object Finished : RelayFrame

    data object Cancelled : RelayFrame

    data class Failed(val message: String) : RelayFrame
}

@Serializable
internal data class RelayToolDto(
    val type: String = "tool",
    val tool: String = "",
    val name: String = "",
    val status: String = "running",
    val detail: String = "",
)

@Serializable
internal data class RelayEventDto(
    val type: String = "",
    val text: String = "",
    val reasoning: String = "",
    val status: String = "",
    val error: String = "",
    val tool: String = "",
    val name: String = "",
    val detail: String = "",
    val tools: List<RelayToolDto> = emptyList(),
)

/**
 * Parses `/api/v1/relay/jobs/{id}/events` payloads.
 *
 * The server sends an initial `snapshot` (full text/reasoning plus the tool
 * history of the job), then live `reasoning`, `delta`, `tool` and `status`
 * events. Snapshot tools are surfaced as a single [StreamEvent.Snapshot] so the
 * ViewModel can replay them without duplicating live data.
 */
internal class RelayEventParser(private val json: Json) {

    fun parse(raw: String): RelayFrame? {
        if (raw.isBlank()) return null
        val event = runCatching { json.decodeFromString<RelayEventDto>(raw) }.getOrNull()
            ?: return null
        return when (event.type) {
            "snapshot" -> parseSnapshot(event)
            "delta" -> event.text.takeIf { it.isNotEmpty() }
                ?.let { RelayFrame.Events(listOf(StreamEvent.Delta(it))) }

            "reasoning" -> {
                val text = event.text.ifBlank { event.reasoning }
                text.takeIf { it.isNotEmpty() }
                    ?.let { RelayFrame.Events(listOf(StreamEvent.Reasoning(it))) }
            }

            "tool" -> toolFrame(event)
            "error" -> RelayFrame.Failed(event.error.ifBlank { "服务端中转失败" })
            "status" -> statusFrame(event)
            else -> null
        }
    }

    private fun parseSnapshot(event: RelayEventDto): RelayFrame? {
        when (event.status.lowercase()) {
            "failed" -> return RelayFrame.Failed(event.error.ifBlank { "服务端中转失败" })
            "cancelled" -> return RelayFrame.Cancelled
        }
        val tools = event.tools.mapNotNull { tool ->
            val name = tool.tool.ifBlank { tool.name }.trim()
            if (name.isBlank()) return@mapNotNull null
            StreamEvent.Tool(
                name = name,
                status = tool.status.ifBlank { "running" },
                detail = tool.detail,
            )
        }
        val hasState = tools.isNotEmpty() ||
            event.text.isNotEmpty() ||
            event.reasoning.isNotEmpty()
        if (!hasState) return null
        return RelayFrame.Events(
            events = listOf(StreamEvent.Snapshot(event.text, event.reasoning, tools)),
            terminal = event.status.equals("completed", ignoreCase = true),
        )
    }

    private fun toolFrame(event: RelayEventDto): RelayFrame? {
        val name = event.tool.ifBlank { event.name }.trim()
        if (name.isBlank()) return null
        return RelayFrame.Events(
            listOf(
                StreamEvent.Tool(
                    name = name,
                    status = event.status.ifBlank { "running" },
                    detail = event.detail,
                )
            )
        )
    }

    private fun statusFrame(event: RelayEventDto): RelayFrame? =
        when (event.status.lowercase()) {
            "completed" -> RelayFrame.Finished
            "cancelled" -> RelayFrame.Cancelled
            "failed" -> RelayFrame.Failed(event.error.ifBlank { "服务端中转失败" })
            // "searching" / "tools" are informational progress markers.
            else -> null
        }
}
