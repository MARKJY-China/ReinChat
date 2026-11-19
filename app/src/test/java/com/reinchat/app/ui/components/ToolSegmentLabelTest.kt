package com.reinchat.app.ui.components

import com.reinchat.app.data.MessageSegment
import com.reinchat.app.data.normalizeToolStatus
import com.reinchat.app.data.thinkingSegmentLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolSegmentLabelTest {

    @Test
    fun webSearchUsesRunningAndFinishedLabels() {
        assertEquals(
            "正在搜索网页",
            toolSegmentLabel("web_search", MessageSegment.TOOL_STATUS_RUNNING),
        )
        assertEquals(
            "搜索网页",
            toolSegmentLabel("web_search", MessageSegment.TOOL_STATUS_DONE),
        )
        assertEquals(
            "搜索网页",
            toolSegmentLabel("web_search", MessageSegment.TOOL_STATUS_FAILED),
        )
    }

    @Test
    fun fetchUrlUsesRunningAndFinishedLabels() {
        assertEquals(
            "正在浏览网页",
            toolSegmentLabel("fetch_url", MessageSegment.TOOL_STATUS_RUNNING),
        )
        assertEquals(
            "浏览网页",
            toolSegmentLabel("fetch_url", MessageSegment.TOOL_STATUS_DONE),
        )
        assertEquals(
            "浏览网页",
            toolSegmentLabel("fetch_url", MessageSegment.TOOL_STATUS_FAILED),
        )
    }

    @Test
    fun unknownToolsPreferServerLabelThenGenericWording() {
        assertEquals(
            "自定义工具",
            toolSegmentLabel("custom_tool", MessageSegment.TOOL_STATUS_RUNNING, fallback = "自定义工具"),
        )
        assertEquals(
            "正在使用工具",
            toolSegmentLabel("custom_tool", MessageSegment.TOOL_STATUS_RUNNING),
        )
        assertEquals(
            "custom_tool",
            toolSegmentLabel("custom_tool", MessageSegment.TOOL_STATUS_DONE),
        )
        assertEquals(
            "正在使用工具",
            toolSegmentLabel("", MessageSegment.TOOL_STATUS_RUNNING),
        )
        assertEquals(
            "工具调用",
            toolSegmentLabel("", MessageSegment.TOOL_STATUS_DONE),
        )
    }

    @Test
    fun segmentOverloadMatchesKnownToolMapping() {
        val running = MessageSegment.tool(
            name = "web_search",
            status = MessageSegment.TOOL_STATUS_RUNNING,
            label = "搜索网页",
        )
        assertEquals("正在搜索网页", toolSegmentLabel(running))
    }

    @Test
    fun relayStatusAliasesAreNormalized() {
        assertEquals(MessageSegment.TOOL_STATUS_RUNNING, normalizeToolStatus("running"))
        assertEquals(MessageSegment.TOOL_STATUS_RUNNING, normalizeToolStatus(""))
        assertEquals(MessageSegment.TOOL_STATUS_DONE, normalizeToolStatus("completed"))
        assertEquals(MessageSegment.TOOL_STATUS_DONE, normalizeToolStatus("SUCCESS"))
        assertEquals(MessageSegment.TOOL_STATUS_FAILED, normalizeToolStatus("error"))
        assertEquals(
            "搜索网页",
            toolSegmentLabel("web_search", "completed"),
        )
        assertEquals(
            "正在浏览网页",
            toolSegmentLabel("fetch_url", "in_progress"),
        )
    }

    @Test
    fun thinkingHeaderUsesRequestedWording() {
        assertEquals("正在思考", thinkingSegmentLabel(active = true, first = true))
        assertEquals("正在思考", thinkingSegmentLabel(active = true, first = false))
        assertEquals("思考过程", thinkingSegmentLabel(active = false, first = true))
        assertEquals("继续思考", thinkingSegmentLabel(active = false, first = false))
    }
}
