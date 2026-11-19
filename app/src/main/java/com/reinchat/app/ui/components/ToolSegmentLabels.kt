package com.reinchat.app.ui.components

import com.reinchat.app.data.MessageSegment

/**
 * Web-parity labels for tool calls. Known tools always use the localized
 * running/finished forms; the server-provided [MessageSegment.label] is only a
 * fallback for tools this client does not know about.
 */
fun toolSegmentLabel(segment: MessageSegment): String = toolSegmentLabel(
    name = segment.name,
    status = segment.status,
    fallback = segment.label,
)

fun toolSegmentLabel(name: String, status: String, fallback: String = ""): String {
    val running = status == MessageSegment.TOOL_STATUS_RUNNING
    return when (name) {
        "web_search" -> if (running) "正在搜索网页" else "搜索网页"
        "fetch_url" -> if (running) "正在浏览网页" else "浏览网页"
        else -> fallback.ifBlank { name }.ifBlank { "工具调用" }
    }
}
