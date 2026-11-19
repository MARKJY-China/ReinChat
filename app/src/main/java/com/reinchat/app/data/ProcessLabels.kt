package com.reinchat.app.data

/** Process wording shared by the streaming UI, tool cards and notifications. */
const val PROCESS_LABEL_THINKING_ACTIVE = "正在思考"
const val PROCESS_LABEL_THINKING_DONE = "思考过程"
const val PROCESS_LABEL_THINKING_CONTINUE = "继续思考"
const val PROCESS_LABEL_SEARCH_ACTIVE = "正在搜索网页"
const val PROCESS_LABEL_SEARCH_DONE = "搜索网页"
const val PROCESS_LABEL_BROWSE_ACTIVE = "正在浏览网页"
const val PROCESS_LABEL_BROWSE_DONE = "浏览网页"
const val PROCESS_LABEL_TOOL_ACTIVE = "正在使用工具"
const val PROCESS_LABEL_TOOL_FALLBACK = "工具调用"

/**
 * The relay and the local seed data use a small, slightly inconsistent set of
 * tool status spellings. Normalize them before they reach the model or the UI.
 */
fun normalizeToolStatus(raw: String): String {
    val status = raw.trim().lowercase()
    return when (status) {
        "", "running", "in_progress", "in-progress", "started", "searching", "browsing" ->
            MessageSegment.TOOL_STATUS_RUNNING

        "done", "completed", "complete", "success", "succeeded", "finished", "ok" ->
            MessageSegment.TOOL_STATUS_DONE

        "failed", "failure", "error", "errored" -> MessageSegment.TOOL_STATUS_FAILED

        else -> raw.trim()
    }
}

/**
 * Web-parity label for a tool segment. Known tools always use the localized
 * running/finished forms; a server-provided [fallback] label wins only for tools
 * this client does not know about.
 */
fun toolSegmentLabel(name: String, status: String, fallback: String = ""): String {
    val normalized = normalizeToolStatus(status)
    val running = normalized == MessageSegment.TOOL_STATUS_RUNNING
    return when (name.trim()) {
        "web_search" -> if (running) PROCESS_LABEL_SEARCH_ACTIVE else PROCESS_LABEL_SEARCH_DONE
        "fetch_url" -> if (running) PROCESS_LABEL_BROWSE_ACTIVE else PROCESS_LABEL_BROWSE_DONE
        else -> fallback.ifBlank {
            if (running) {
                PROCESS_LABEL_TOOL_ACTIVE
            } else {
                name.trim().ifBlank { PROCESS_LABEL_TOOL_FALLBACK }
            }
        }
    }
}

fun toolSegmentLabel(segment: MessageSegment): String =
    toolSegmentLabel(segment.name, segment.status, segment.label)

/**
 * Header of a thinking block. While the block is still receiving tokens (and no
 * answer text exists yet) it reads "正在思考"; completed blocks keep the web
 * client's "思考过程"/"继续思考" wording.
 */
fun thinkingSegmentLabel(active: Boolean, first: Boolean): String = when {
    active -> PROCESS_LABEL_THINKING_ACTIVE
    first -> PROCESS_LABEL_THINKING_DONE
    else -> PROCESS_LABEL_THINKING_CONTINUE
}
