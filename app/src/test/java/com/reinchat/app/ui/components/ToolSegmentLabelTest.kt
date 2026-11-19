package com.reinchat.app.ui.components

import com.reinchat.app.data.MessageSegment
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
    fun unknownToolsPreferServerLabelThenName() {
        assertEquals(
            "自定义工具",
            toolSegmentLabel("custom_tool", MessageSegment.TOOL_STATUS_RUNNING, fallback = "自定义工具"),
        )
        assertEquals(
            "custom_tool",
            toolSegmentLabel("custom_tool", MessageSegment.TOOL_STATUS_RUNNING),
        )
        assertEquals(
            "工具调用",
            toolSegmentLabel("", MessageSegment.TOOL_STATUS_RUNNING),
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
}
