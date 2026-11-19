package com.reinchat.app.data

/**
 * Ordered reasoning/tool timeline of one assistant message.
 *
 * The relay streams thinking deltas and tool state changes interleaved. The
 * timeline keeps that order: a tool update always appends *after* the thinking
 * block that preceded it, and the next reasoning delta starts a new block, so
 * the UI can render "thinking A → tool → thinking B → final answer".
 *
 * It is a plain class (no Android dependencies) so the relay → segment pipeline
 * can be unit-tested and driven by the debug probe.
 */
class ProcessTimeline {

    private val items = mutableListOf<MessageSegment>()

    val segments: List<MessageSegment>
        get() = items.toList()

    val isEmpty: Boolean
        get() = items.isEmpty()

    val hasReasoning: Boolean
        get() = items.any { it.type == MessageSegment.TYPE_REASONING }

    /** Appends a thinking delta, merging into the trailing thinking block. */
    fun appendReasoning(text: String) {
        if (text.isEmpty()) return
        val last = items.lastOrNull()
        if (last != null && last.type == MessageSegment.TYPE_REASONING) {
            items[items.size - 1] = last.copy(text = last.text + text)
        } else {
            // A tool card (or the start of the message) ends the previous block.
            items += MessageSegment.reasoning(text)
        }
    }

    /**
     * Seeds the full thinking text carried by a relay `snapshot` only when this
     * client has not received any reasoning yet; otherwise the live deltas are
     * already in the timeline and replaying the snapshot would duplicate them.
     */
    fun seedReasoning(text: String) {
        if (text.isBlank()) return
        if (!hasReasoning) appendReasoning(text)
    }

    /**
     * Applies a tool state change. A `running` event creates a card; the later
     * `done`/`failed` event updates the same card in place. Replayed snapshots of
     * a finished call update the existing finished card instead of adding a
     * duplicate.
     */
    fun upsertTool(name: String, status: String, detail: String) {
        val toolName = name.trim().ifBlank { "tool" }
        val normalized = normalizeToolStatus(status)
        val trimmedDetail = detail.trim().take(MAX_DETAIL_CHARS)

        val runningIndex = items.indexOfLast {
            it.type == MessageSegment.TYPE_TOOL &&
                it.name == toolName &&
                normalizeToolStatus(it.status) == MessageSegment.TOOL_STATUS_RUNNING
        }
        if (runningIndex >= 0) {
            val existing = items[runningIndex]
            items[runningIndex] = existing.copy(
                status = normalized,
                label = toolSegmentLabel(toolName, normalized, existing.label),
                detail = trimmedDetail.ifBlank { existing.detail },
            )
            return
        }

        if (normalized == MessageSegment.TOOL_STATUS_RUNNING) {
            items += MessageSegment.tool(
                name = toolName,
                status = normalized,
                detail = trimmedDetail,
                label = toolSegmentLabel(toolName, normalized),
            )
            return
        }

        // A finished call with no running card was most likely replayed from a
        // snapshot; update the last finished card of the same tool in place.
        val finishedIndex = items.indexOfLast {
            it.type == MessageSegment.TYPE_TOOL &&
                it.name == toolName &&
                normalizeToolStatus(it.status) != MessageSegment.TOOL_STATUS_RUNNING
        }
        if (finishedIndex >= 0) {
            val existing = items[finishedIndex]
            items[finishedIndex] = existing.copy(
                status = normalized,
                label = toolSegmentLabel(toolName, normalized, existing.label),
                detail = trimmedDetail.ifBlank { existing.detail },
            )
        } else {
            items += MessageSegment.tool(
                name = toolName,
                status = normalized,
                detail = trimmedDetail,
                label = toolSegmentLabel(toolName, normalized),
            )
        }
    }

    /** Replaces the timeline, used when replaying persisted segments. */
    fun replaceAll(segments: List<MessageSegment>) {
        items.clear()
        items += segments
    }

    companion object {
        private const val MAX_DETAIL_CHARS = 4000
    }
}
