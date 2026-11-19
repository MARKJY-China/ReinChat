package com.reinchat.app.ui.components

import com.reinchat.app.data.MessageSegment
import com.reinchat.app.data.toolSegmentLabel as dataToolSegmentLabel

/**
 * Compose-facing wrappers around the shared process labels in
 * `com.reinchat.app.data.ProcessLabels` (the relay/ViewModel layer uses the same
 * mapping so persisted labels and live labels never disagree).
 */
fun toolSegmentLabel(segment: MessageSegment): String = dataToolSegmentLabel(segment)

fun toolSegmentLabel(name: String, status: String, fallback: String = ""): String =
    dataToolSegmentLabel(name, status, fallback)
