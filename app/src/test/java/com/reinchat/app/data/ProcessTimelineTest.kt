package com.reinchat.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessTimelineTest {

    @Test
    fun interleavedReasoningAndToolsKeepChronologicalOrder() {
        val timeline = ProcessTimeline()
        timeline.appendReasoning("先分析问题")
        timeline.upsertTool("web_search", "running", "液态玻璃 设计原则")
        timeline.upsertTool("web_search", "done", "1. 透光\n2. 折射")
        timeline.appendReasoning("搜索确认了三个关键词")
        timeline.upsertTool("fetch_url", "running", "https://example.com/glass")
        timeline.upsertTool("fetch_url", "failed", "连接超时（10s）")
        timeline.appendReasoning("页面打不开，按已有资料回答")

        val segments = timeline.segments
        assertEquals(5, segments.size)
        assertEquals(MessageSegment.TYPE_REASONING, segments[0].type)
        assertEquals("先分析问题", segments[0].text)

        assertEquals(MessageSegment.TYPE_TOOL, segments[1].type)
        assertEquals("web_search", segments[1].name)
        assertEquals(MessageSegment.TOOL_STATUS_DONE, segments[1].status)
        assertEquals("搜索网页", segments[1].label)
        assertTrue(segments[1].detail.contains("透光"))

        assertEquals(MessageSegment.TYPE_REASONING, segments[2].type)
        assertEquals("搜索确认了三个关键词", segments[2].text)

        assertEquals(MessageSegment.TYPE_TOOL, segments[3].type)
        assertEquals("fetch_url", segments[3].name)
        assertEquals(MessageSegment.TOOL_STATUS_FAILED, segments[3].status)
        assertEquals("浏览网页", segments[3].label)

        assertEquals(MessageSegment.TYPE_REASONING, segments[4].type)
        assertEquals("页面打不开，按已有资料回答", segments[4].text)
    }

    @Test
    fun toolBlockEndsThePrecedingThinkingBlock() {
        val timeline = ProcessTimeline()
        timeline.appendReasoning("A")
        timeline.upsertTool("web_search", "running", "")
        timeline.appendReasoning("B")
        timeline.upsertTool("web_search", "done", "")

        val segments = timeline.segments
        assertEquals(3, segments.size)
        assertEquals("A", segments[0].text)
        assertEquals("B", segments[2].text)
        assertFalse(segments[0].text.contains("B"))
        // The trailing `done` event updates the running card instead of adding
        // another one, so the tool card stays between A and B.
        assertEquals(MessageSegment.TYPE_TOOL, segments[1].type)
        assertEquals(MessageSegment.TOOL_STATUS_DONE, segments[1].status)
    }

    @Test
    fun runningLabelBecomesFinishedOnTheSameCard() {
        val timeline = ProcessTimeline()
        timeline.upsertTool("web_search", "running", "")
        assertEquals("正在搜索网页", timeline.segments.single().label)

        timeline.upsertTool("web_search", "failed", "网络错误")
        val tool = timeline.segments.single()
        assertEquals(MessageSegment.TOOL_STATUS_FAILED, tool.status)
        assertEquals("搜索网页", tool.label)
        assertEquals("网络错误", tool.detail)
    }

    @Test
    fun replayedSnapshotDoesNotDuplicateFinishedTools() {
        val timeline = ProcessTimeline()
        timeline.upsertTool("web_search", "done", "结果")
        timeline.upsertTool("fetch_url", "done", "页面")

        // A reconnect replays the same tool history in the snapshot.
        timeline.upsertTool("web_search", "done", "结果")
        timeline.upsertTool("fetch_url", "done", "页面")

        assertEquals(2, timeline.segments.size)
        assertEquals(listOf("web_search", "fetch_url"), timeline.segments.map { it.name })
    }

    @Test
    fun snapshotReasoningSeedsOnlyWhenNothingArrivedLive() {
        val timeline = ProcessTimeline()
        timeline.appendReasoning("live delta")
        timeline.seedReasoning("live deltalive delta")
        assertEquals("live delta", timeline.segments.single().text)

        val empty = ProcessTimeline()
        empty.seedReasoning("snapshot text")
        assertEquals("snapshot text", empty.segments.single().text)
        assertEquals(MessageSegment.TYPE_REASONING, empty.segments.single().type)
    }

    @Test
    fun normalizedAliasesAndDetailTruncation() {
        val timeline = ProcessTimeline()
        timeline.upsertTool("custom_tool", "in_progress", "x".repeat(5000))
        val running = timeline.segments.single()
        assertEquals(MessageSegment.TOOL_STATUS_RUNNING, running.status)
        assertEquals(MessageSegment.TOOL_STATUS_RUNNING, normalizeToolStatus(running.status))
        assertEquals(4000, running.detail.length)

        timeline.upsertTool("custom_tool", "COMPLETED", "")
        assertEquals(MessageSegment.TOOL_STATUS_DONE, timeline.segments.single().status)
        assertEquals("x".repeat(4000), timeline.segments.single().detail)
    }


    @Test
    fun interimTextStaysInWorkHistoryAndFinalTextIsTrailing() {
        val timeline = ProcessTimeline()
        timeline.appendReasoning("plan")
        timeline.appendText("interim report")
        timeline.upsertTool("web_search", "running", "q")
        timeline.upsertTool("web_search", "done", "results")
        timeline.appendReasoning("more thinking")
        timeline.appendText("final answer")

        val work = timeline.workSegments()
        assertEquals(4, work.size)
        assertEquals(MessageSegment.TYPE_REASONING, work[0].type)
        assertEquals(MessageSegment.TYPE_TEXT, work[1].type)
        assertEquals("interim report", work[1].text)
        assertEquals(MessageSegment.TYPE_TOOL, work[2].type)
        assertEquals(MessageSegment.TYPE_REASONING, work[3].type)
        assertEquals("final answer", timeline.trailingText())
        assertTrue(timeline.hasText)
    }

    @Test
    fun textDeltasMergeIntoOneBlock() {
        val timeline = ProcessTimeline()
        timeline.appendText("hello ")
        timeline.appendText("world")
        assertEquals(1, timeline.segments.count { it.type == MessageSegment.TYPE_TEXT })
        assertEquals("hello world", timeline.segments[0].text)
        assertEquals("", timeline.trailingText())
        assertTrue(timeline.workSegments().isEmpty())
    }


    @Test
    fun answerTextPrefersTrailingBlocksAndFallsBackForLegacyMessages() {
        val legacy = listOf(
            MessageSegment.reasoning("plan"),
            MessageSegment.tool("web_search", MessageSegment.TOOL_STATUS_DONE, "results"),
        )
        assertEquals("stored body", ProcessTimeline.answerText(legacy, "stored body"))

        val modern = legacy + MessageSegment.text("final answer")
        assertEquals("final answer", ProcessTimeline.answerText(modern, "stored body"))
        assertEquals(listOf(MessageSegment.TYPE_REASONING, MessageSegment.TYPE_TOOL), modern.let { ProcessTimeline.splitAnswer(it).first }.map { it.type })
    }
}
