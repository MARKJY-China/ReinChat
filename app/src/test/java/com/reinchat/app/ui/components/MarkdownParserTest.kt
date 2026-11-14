package com.reinchat.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parses common block types`() {
        val blocks = MarkdownParser.parse(
            """
            # 标题

            一段 **加粗** 文字，带 `代码` 与 [链接](https://example.com)。

            - 第一项
            - 第二项

            1. 第一步
            2. 第二步

            > 引用内容

            ```kotlin
            val x = 1
            ```
            ---
            """.trimIndent()
        )
        assertEquals(7, blocks.size)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertTrue(blocks[2] is MdBlock.BulletList)
        assertTrue(blocks[3] is MdBlock.OrderedList)
        assertTrue(blocks[4] is MdBlock.Quote)
        assertTrue(blocks[5] is MdBlock.CodeBlock)
        assertEquals(MdBlock.Divider, blocks[6])
    }

    @Test
    fun `parses inline formatting recursively`() {
        val inline = MarkdownParser.parseInline("**粗体** 和 *斜体* 以及 `x` 和 [站点](https://a.dev)")
        assertTrue(inline.any { it is MdInline.Strong })
        assertTrue(inline.any { it is MdInline.Emphasis })
        assertTrue(inline.any { it is MdInline.Code })
        assertTrue(inline.any { it is MdInline.Link })
    }

    @Test
    fun `keeps unfinished streaming code block`() {
        val blocks = MarkdownParser.parse("下面是一段代码：\n\n```python\nprint('hi')\nprint(")
        assertEquals(2, blocks.size)
        val code = blocks[1] as MdBlock.CodeBlock
        assertEquals("python", code.language)
        assertTrue(code.code.contains("print('hi')"))
        assertTrue(!code.closed)
    }

    @Test
    fun `parses headings without a space after hashes`() {
        val blocks = MarkdownParser.parse("###量子纠缠是什么？\n###关键点1. 不是超光速通信")
        assertEquals(2, blocks.size)
        val first = blocks[0] as MdBlock.Heading
        assertEquals(3, first.level)
        assertEquals("量子纠缠是什么？", inlineText(first.inlines))
        assertTrue(blocks[1] is MdBlock.Heading)
    }

    @Test
    fun `headings interrupt paragraphs without blank lines`() {
        val blocks = MarkdownParser.parse(
            "先说结论。\n###量子纠缠是什么？\n简单说：两个粒子形成一个整体。\n###关键点1. 不是超光速通信"
        )
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
        assertTrue(blocks[1] is MdBlock.Heading)
        val heading = blocks[1] as MdBlock.Heading
        assertEquals("量子纠缠是什么？", inlineText(heading.inlines))
        assertTrue(blocks[2] is MdBlock.Paragraph)
        // The next heading should also be recognized, even without a blank line.
        assertTrue(blocks[3] is MdBlock.Heading)
    }

    @Test
    fun `parses markdown tables with alignment`() {
        val blocks = MarkdownParser.parse(
            """
            | 名称 | 数值 | 说明 |
            | :--- | ---: | :---: |
            | 延迟 | 12ms | 好 |
            | 吞吐 | 100 | 稳定 |
            """.trimIndent()
        )
        assertEquals(1, blocks.size)
        val table = blocks[0] as MdBlock.Table
        assertEquals(3, table.header.size)
        assertEquals(2, table.rows.size)
        assertEquals(MdAlign.LEFT, table.alignments[0])
        assertEquals(MdAlign.RIGHT, table.alignments[1])
        assertEquals(MdAlign.CENTER, table.alignments[2])
        assertEquals("延迟", inlineText(table.rows[0][0]))
    }

    @Test
    fun `unmatched emphasis remains visible`() {
        assertEquals("2 * 3 * 4", inlineText(MarkdownParser.parseInline("2 * 3 * 4")))
    }

    private fun inlineText(inlines: List<MdInline>): String = inlines.joinToString("") {
        when (it) {
            is MdInline.Plain -> it.text
            is MdInline.Strong -> inlineText(it.children)
            is MdInline.Emphasis -> inlineText(it.children)
            is MdInline.Strike -> inlineText(it.children)
            is MdInline.Code -> it.text
            is MdInline.Link -> inlineText(it.label)
        }
    }
}
