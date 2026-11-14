package com.reinchat.app.ui.components

sealed interface MdInline {
    data class Plain(val text: String) : MdInline
    data class Strong(val children: List<MdInline>) : MdInline
    data class Emphasis(val children: List<MdInline>) : MdInline
    data class Strike(val children: List<MdInline>) : MdInline
    data class Code(val text: String) : MdInline
    data class Link(val label: List<MdInline>, val url: String) : MdInline
}

enum class MdAlign { LEFT, CENTER, RIGHT }

sealed interface MdBlock {
    data class Paragraph(val inlines: List<MdInline>) : MdBlock
    data class Heading(val level: Int, val inlines: List<MdInline>) : MdBlock
    data class BulletList(val items: List<List<MdInline>>) : MdBlock
    data class OrderedList(val start: Int, val items: List<List<MdInline>>) : MdBlock
    data class Quote(val inlines: List<MdInline>) : MdBlock
    data class CodeBlock(val language: String, val code: String, val closed: Boolean) : MdBlock
    data class Table(
        val header: List<List<MdInline>>,
        val rows: List<List<List<MdInline>>>,
        val alignments: List<MdAlign>,
    ) : MdBlock
    data object Divider : MdBlock
}

/**
 * A deliberately small, streaming-safe CommonMark subset. It covers the blocks
 * that actually show up in model answers and tolerates an unfinished final
 * block while tokens are still arriving.
 */
object MarkdownParser {

    // Many models emit "###标题" without the CommonMark-required space.
    private val heading = Regex("""^\s{0,3}(#{1,6})\s*(.*)$""")
    private val bullet = Regex("""^\s{0,3}[-*+]\s+(.*)$""")
    private val ordered = Regex("""^\s{0,3}(\d{1,3})[.)]\s+(.*)$""")
    private val quote = Regex("""^\s{0,3}>\s?(.*)$""")
    private val divider = Regex("""^\s{0,3}([-*_])(?:\s*\1){2,}\s*$""")
    private val fence = Regex("""^\s{0,3}```(.*)$""")
    private val tableSeparatorCell = Regex("""^\s*:?-{2,}:?\s*$""")

    private val dsmlCalls = Regex("""(?is)<[^>]*DSML[^>]*calls[^>]*>.*?</[^>]*DSML[^>]*calls[^>]*>""")
    private val dsmlTag = Regex("""(?is)<[^>]*DSML[^>]*>""")

    fun sanitizeDSML(source: String): String =
        source.replace(dsmlCalls, "").replace(dsmlTag, "").trim()

    fun parse(source: String): List<MdBlock> {
        if (source.isEmpty()) return emptyList()
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }

            val fenceMatch = fence.matchEntire(line)
            if (fenceMatch != null) {
                val language = fenceMatch.groupValues[1].trim()
                val code = StringBuilder()
                i++
                var closed = false
                while (i < lines.size) {
                    val candidate = lines[i]
                    if (candidate.trim() == "```") {
                        closed = true
                        i++
                        break
                    }
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(candidate)
                    i++
                }
                blocks += MdBlock.CodeBlock(language, code.toString(), closed)
                continue
            }

            val headingMatch = heading.matchEntire(line)
            if (headingMatch != null && headingMatch.groupValues[2].isNotBlank()) {
                blocks += MdBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    inlines = parseInline(headingMatch.groupValues[2].trim()),
                )
                i++
                continue
            }

            if (isTableStart(lines, i)) {
                val headerCells = splitTableRow(line)
                val separatorCells = splitTableRow(lines[i + 1])
                val alignments = separatorCells.map { cell ->
                    val trimmed = cell.trim()
                    when {
                        trimmed.startsWith(":") && trimmed.endsWith(":") -> MdAlign.CENTER
                        trimmed.endsWith(":") -> MdAlign.RIGHT
                        else -> MdAlign.LEFT
                    }
                }
                i += 2
                val rows = mutableListOf<List<List<MdInline>>>()
                while (i < lines.size && lines[i].contains('|')) {
                    val cells = splitTableRow(lines[i])
                    if (cells.isEmpty()) break
                    rows += List(headerCells.size) { index ->
                        parseInline(cells.getOrElse(index) { "" })
                    }
                    i++
                }
                blocks += MdBlock.Table(
                    header = headerCells.map(::parseInline),
                    rows = rows,
                    alignments = List(headerCells.size) { alignments.getOrElse(it) { MdAlign.LEFT } },
                )
                continue
            }

            if (divider.matches(line)) {
                blocks += MdBlock.Divider
                i++
                continue
            }

            if (bullet.matches(line)) {
                val items = mutableListOf<List<MdInline>>()
                while (i < lines.size) {
                    val match = bullet.matchEntire(lines[i]) ?: break
                    items += parseInline(match.groupValues[1].trim())
                    i++
                }
                blocks += MdBlock.BulletList(items)
                continue
            }

            val orderedFirst = ordered.matchEntire(line)
            if (orderedFirst != null) {
                val start = orderedFirst.groupValues[1].toIntOrNull() ?: 1
                val items = mutableListOf<List<MdInline>>()
                while (i < lines.size) {
                    val match = ordered.matchEntire(lines[i]) ?: break
                    items += parseInline(match.groupValues[2].trim())
                    i++
                }
                blocks += MdBlock.OrderedList(start, items)
            }
            if (blocks.lastOrNull() is MdBlock.OrderedList && ordered.matches(line)) continue

            if (quote.matches(line)) {
                val quoted = mutableListOf<String>()
                while (i < lines.size) {
                    val match = quote.matchEntire(lines[i]) ?: break
                    quoted += match.groupValues[1]
                    i++
                }
                blocks += MdBlock.Quote(parseInline(quoted.joinToString("\n").trim()))
                continue
            }

            val paragraph = mutableListOf<String>()
            while (i < lines.size) {
                val candidate = lines[i]
                if (candidate.isBlank() ||
                    fence.containsMatchIn(candidate) ||
                    heading.containsMatchIn(candidate) ||
                    bullet.containsMatchIn(candidate) ||
                    ordered.containsMatchIn(candidate) ||
                    quote.containsMatchIn(candidate) ||
                    divider.matches(candidate) ||
                    isTableStart(lines, i)
                ) {
                    break
                }
                paragraph += candidate.trim()
                i++
            }
            if (paragraph.isEmpty()) {
                i++
            } else {
                blocks += MdBlock.Paragraph(parseInline(paragraph.joinToString("\n")))
            }
        }
        return blocks
    }

    private fun isTableStart(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        val header = lines[index]
        if (!header.contains('|')) return false
        val headerCells = splitTableRow(header)
        val separatorCells = splitTableRow(lines[index + 1])
        if (headerCells.size < 2 || headerCells.size != separatorCells.size) return false
        return separatorCells.all { tableSeparatorCell.matches(it) }
    }

    private fun splitTableRow(line: String): List<String> {
        var value = line.trim()
        if (value.startsWith("|")) value = value.substring(1)
        if (value.endsWith("|")) value = value.dropLast(1)
        return value.split('|').map { it.trim() }
    }

    fun parseInline(source: String): List<MdInline> {
        val result = mutableListOf<MdInline>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                result += MdInline.Plain(plain.toString())
                plain.clear()
            }
        }

        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (c == '\\' && i + 1 < source.length) {
                plain.append(source[i + 1])
                i += 2
                continue
            }

            if (c == '`') {
                val end = source.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush()
                    result += MdInline.Code(source.substring(i + 1, end).trim())
                    i = end + 1
                    continue
                }
            }

            if ((source.startsWith("**", i) || source.startsWith("__", i)) &&
                i + 2 < source.length && !source[i + 2].isWhitespace()
            ) {
                val marker = source.substring(i, i + 2)
                val end = source.indexOf(marker, i + 2)
                if (end > i + 2 && !source[end - 1].isWhitespace()) {
                    flush()
                    result += MdInline.Strong(parseInline(source.substring(i + 2, end)))
                    i = end + 2
                    continue
                }
            }

            if (source.startsWith("~~", i) && i + 2 < source.length && !source[i + 2].isWhitespace()) {
                val end = source.indexOf("~~", i + 2)
                if (end > i + 2 && !source[end - 1].isWhitespace()) {
                    flush()
                    result += MdInline.Strike(parseInline(source.substring(i + 2, end)))
                    i = end + 2
                    continue
                }
            }

            if (c == '*' || c == '_') {
                val canOpen = i + 1 < source.length && !source[i + 1].isWhitespace() &&
                    (c != '_' || i == 0 || !source[i - 1].isLetterOrDigit())
                if (canOpen) {
                    val end = source.indexOf(c, i + 1)
                    if (end > i + 1 && !source[end - 1].isWhitespace()) {
                        flush()
                        result += MdInline.Emphasis(parseInline(source.substring(i + 1, end)))
                        i = end + 1
                        continue
                    }
                }
            }

            if (c == '[') {
                val closeLabel = source.indexOf(']', i + 1)
                if (closeLabel > i && closeLabel + 1 < source.length && source[closeLabel + 1] == '(') {
                    val closeUrl = source.indexOf(')', closeLabel + 2)
                    if (closeUrl > closeLabel + 2) {
                        val label = source.substring(i + 1, closeLabel)
                        val url = source.substring(closeLabel + 2, closeUrl).trim()
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            flush()
                            result += MdInline.Link(parseInline(label), url)
                            i = closeUrl + 1
                            continue
                        }
                    }
                }
            }

            plain.append(c)
            i++
        }
        flush()
        return result
    }
}
