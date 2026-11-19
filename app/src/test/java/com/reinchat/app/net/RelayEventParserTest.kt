package com.reinchat.app.net

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayEventParserTest {

    private val parser = RelayEventParser(Json { ignoreUnknownKeys = true })

    @Test
    fun snapshotCarriesToolHistoryTextAndReasoning() {
        val frame = parser.parse(
            """
            {"type":"snapshot","status":"running","text":"已生成的部分",
             "reasoning":"先想一想",
             "tools":[
               {"type":"tool","tool":"web_search","status":"done","detail":"搜索结果"},
               {"type":"tool","tool":"fetch_url","status":"failed","detail":"超时"}
             ]}
            """.trimIndent()
        )
        assertTrue(frame is RelayFrame.Events)
        val snapshot = (frame as RelayFrame.Events).events.single() as StreamEvent.Snapshot
        assertEquals("已生成的部分", snapshot.text)
        assertEquals("先想一想", snapshot.reasoning)
        assertEquals(listOf("web_search", "fetch_url"), snapshot.tools.map { it.name })
        assertEquals(listOf("done", "failed"), snapshot.tools.map { it.status })
        assertEquals("搜索结果", snapshot.tools[0].detail)
        assertTrue(!frame.terminal)
    }

    @Test
    fun completedSnapshotTerminatesAfterReplayingState() {
        val frame = parser.parse(
            """{"type":"snapshot","status":"completed","text":"answer",
                "tools":[{"type":"tool","tool":"web_search","status":"done"}]}"""
        )
        assertTrue(frame is RelayFrame.Events)
        frame as RelayFrame.Events
        assertTrue(frame.terminal)
        assertEquals(1, frame.events.size)
        assertEquals("answer", (frame.events.single() as StreamEvent.Snapshot).text)
    }

    @Test
    fun emptySnapshotIsIgnored() {
        assertNull(parser.parse("""{"type":"snapshot","status":"running"}"""))
    }

    @Test
    fun liveReasoningToolAndDeltaEvents() {
        val reasoning = parser.parse("""{"type":"reasoning","text":"思考 A"}""")
        assertEquals(
            StreamEvent.Reasoning("思考 A"),
            (reasoning as RelayFrame.Events).events.single(),
        )

        // Some server variants put the delta in `reasoning` instead of `text`.
        val reasoningAlt = parser.parse("""{"type":"reasoning","reasoning":"思考 B"}""")
        assertEquals(
            StreamEvent.Reasoning("思考 B"),
            (reasoningAlt as RelayFrame.Events).events.single(),
        )

        val tool = parser.parse(
            """{"type":"tool","tool":"web_search","status":"running","detail":"query"}"""
        )
        assertEquals(
            StreamEvent.Tool("web_search", "running", "query"),
            (tool as RelayFrame.Events).events.single(),
        )

        val delta = parser.parse("""{"type":"delta","text":"最终回答"}""")
        assertEquals(
            StreamEvent.Delta("最终回答"),
            (delta as RelayFrame.Events).events.single(),
        )
    }

    @Test
    fun toolEventFallsBackToNameField() {
        val frame = parser.parse("""{"type":"tool","name":"fetch_url","status":"done"}""")
        val tool = (frame as RelayFrame.Events).events.single() as StreamEvent.Tool
        assertEquals("fetch_url", tool.name)
        assertEquals("done", tool.status)
    }

    @Test
    fun terminalStatuses() {
        assertEquals(RelayFrame.Finished, parser.parse("""{"type":"status","status":"completed"}"""))
        assertEquals(RelayFrame.Cancelled, parser.parse("""{"type":"status","status":"cancelled"}"""))
        assertEquals(
            RelayFrame.Failed("上游失败"),
            parser.parse("""{"type":"status","status":"failed","error":"上游失败"}"""),
        )
        assertEquals(
            RelayFrame.Failed("relay broke"),
            parser.parse("""{"type":"error","error":"relay broke"}"""),
        )
        assertEquals(
            RelayFrame.Failed("失败"),
            parser.parse("""{"type":"snapshot","status":"failed","error":"失败"}"""),
        )
    }

    @Test
    fun progressStatusesAndGarbageAreIgnored() {
        assertNull(parser.parse("""{"type":"status","status":"searching"}"""))
        assertNull(parser.parse("""{"type":"status","status":"tools"}"""))
        assertNull(parser.parse("not json"))
        assertNull(parser.parse(""))
    }

    @Test
    fun snapshotToolsPopulateTheSegmentTimeline() {
        // Mirrors a reconnect after the job already ran web_search + fetch_url:
        // the snapshot carries the whole tool history (running + done pairs).
        val frame = parser.parse(
            """
            {"type":"snapshot","status":"running","text":"","reasoning":"",
             "tools":[
               {"type":"tool","tool":"web_search","status":"running","detail":"query"},
               {"type":"tool","tool":"web_search","status":"done","detail":"结果"},
               {"type":"tool","tool":"fetch_url","status":"running","detail":"https://example.com"},
               {"type":"tool","tool":"fetch_url","status":"failed","detail":"超时"}
             ]}
            """.trimIndent()
        ) as RelayFrame.Events
        val snapshot = frame.events.single() as StreamEvent.Snapshot

        val timeline = com.reinchat.app.data.ProcessTimeline()
        snapshot.tools.forEach { timeline.upsertTool(it.name, it.status, it.detail) }
        timeline.seedReasoning(snapshot.reasoning)

        val segments = timeline.segments
        assertEquals(2, segments.size)
        assertEquals("web_search", segments[0].name)
        assertEquals("done", segments[0].status)
        assertEquals("搜索网页", segments[0].label)
        assertEquals("fetch_url", segments[1].name)
        assertEquals("failed", segments[1].status)
        assertEquals("浏览网页", segments[1].label)

        // A live reasoning event after the replay opens a new thinking block.
        timeline.appendReasoning("继续思考")
        assertEquals(3, timeline.segments.size)
        assertEquals("继续思考", timeline.segments.last().text)
    }
}
