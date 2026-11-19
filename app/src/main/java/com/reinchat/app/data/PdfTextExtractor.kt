package com.reinchat.app.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.zip.Inflater

/** Best-effort local PDF text extraction result. */
data class PdfTextResult(
    val text: String,
    val pages: Int,
) {
    val hasText: Boolean
        get() = text.isNotBlank()
}

/**
 * Dependency-free, best-effort text extraction for straightforward PDFs.
 *
 * It scans the raw file for stream objects, inflates FlateDecode streams and
 * reads the strings used by the content-stream text operators (Tj/TJ/'/").
 * It intentionally does not implement the full PDF object model, xref streams,
 * encryption or CID/ToUnicode CMaps: when it cannot recover plausible text the
 * caller must fall back to the server OCR endpoint. This keeps the APK free of
 * a multi-megabyte native PDF library while still handling ordinary text PDFs.
 */
object PdfTextExtractor {

    // Reading the file as a 1-byte-per-char String needs ~2x memory; keep the
    // local pass bounded and let large/scanned PDFs fall back to server OCR.
    private const val MAX_RAW_BYTES = 24 * 1024 * 1024
    private const val MAX_TEXT_CHARS = 60_000
    private const val MAX_STREAM_BYTES = 16 * 1024 * 1024

    private val WINDOWS_1252: Charset = runCatching { Charset.forName("windows-1252") }
        .getOrDefault(Charsets.ISO_8859_1)

    fun extract(file: File): PdfTextResult {
        if (!file.isFile) return PdfTextResult("", 0)
        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?: return PdfTextResult("", 0)
        return extract(bytes)
    }

    fun extract(bytes: ByteArray): PdfTextResult {
        if (bytes.isEmpty() || bytes.size > MAX_RAW_BYTES) return PdfTextResult("", 0)
        val raw = String(bytes, Charsets.ISO_8859_1)
        if (!raw.contains("%PDF-")) return PdfTextResult("", 0)

        val pages = estimatePages(raw)
        val chunks = StringBuilder()
        var cursor = 0
        while (chunks.length < MAX_TEXT_CHARS) {
            val streamKeyword = findStreamKeyword(raw, cursor) ?: break
            val dictStart = raw.lastIndexOf("<<", streamKeyword)
            if (dictStart < 0 || streamKeyword - dictStart > 8192) {
                cursor = streamKeyword + 6
                continue
            }
            val dictEnd = raw.indexOf(">>", dictStart)
            if (dictEnd < 0 || dictEnd > streamKeyword) {
                cursor = streamKeyword + 6
                continue
            }
            val dict = raw.substring(dictStart, dictEnd)
            val dataStart = skipStreamEol(raw, streamKeyword + "stream".length)
            val declaredLength = Regex("/Length\\s+(\\d+)").find(dict)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            val dataEnd = chooseStreamEnd(raw, dataStart, declaredLength)
            if (dataEnd <= dataStart) {
                cursor = streamKeyword + 6
                continue
            }
            cursor = dataEnd + 1
            if (dataEnd - dataStart > MAX_STREAM_BYTES) continue

            val data = raw.substring(dataStart, dataEnd)
            val decoded = if (dict.contains("/FlateDecode")) inflate(data) else data.toByteArray(Charsets.ISO_8859_1)
            if (decoded == null || decoded.isEmpty()) continue
            val content = String(decoded, Charsets.ISO_8859_1)
            if (!looksLikeContentStream(content)) continue
            val text = extractContentText(content)
            if (text.isNotBlank()) {
                if (chunks.isNotEmpty()) chunks.append('\n')
                chunks.append(text)
            }
        }

        val cleaned = normalize(chunks.toString()).take(MAX_TEXT_CHARS)
        return if (isPlausible(cleaned)) {
            PdfTextResult(cleaned, pages)
        } else {
            PdfTextResult("", pages)
        }
    }

    private fun findStreamKeyword(raw: String, from: Int): Int? {
        var index = raw.indexOf("stream", from)
        while (index >= 0) {
            val isEndStream = index >= 3 && raw.regionMatches(index - 3, "end", 0, 3)
            // "stream" must be a standalone keyword for a stream object.
            val next = index + 6
            val nextChar = raw.getOrNull(next)
            if (!isEndStream && (nextChar == '\r' || nextChar == '\n' || nextChar == null)) {
                return index
            }
            index = raw.indexOf("stream", index + 6)
        }
        return null
    }

    private fun skipStreamEol(raw: String, start: Int): Int {
        var index = start
        if (raw.getOrNull(index) == '\r') index++
        if (raw.getOrNull(index) == '\n') index++
        return index
    }

    private fun chooseStreamEnd(raw: String, dataStart: Int, declaredLength: Int?): Int {
        if (declaredLength != null && declaredLength > 0 &&
            dataStart + declaredLength <= raw.length
        ) {
            val candidate = dataStart + declaredLength
            // Trust /Length only when "endstream" follows within a small gap.
            val tail = raw.substring(candidate, (candidate + 32).coerceAtMost(raw.length))
            if (tail.trimStart('\r', '\n').startsWith("endstream")) return candidate
        }
        val marker = raw.indexOf("endstream", dataStart)
        if (marker < 0) return raw.length
        var end = marker
        while (end > dataStart && (raw[end - 1] == '\r' || raw[end - 1] == '\n')) end--
        return end
    }

    private fun inflate(data: String): ByteArray? {
        val bytes = data.toByteArray(Charsets.ISO_8859_1)
        val inflater = Inflater()
        return try {
            inflater.setInput(bytes)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                if (read == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                output.write(buffer, 0, read)
                if (output.size() > MAX_STREAM_BYTES) break
            }
            output.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun looksLikeContentStream(content: String): Boolean {
        if (!content.contains("BT")) return false
        return content.contains("Tj") || content.contains("TJ") ||
            content.contains("'") || content.contains("\"")
    }

    private fun extractContentText(content: String): String {
        val out = StringBuilder()
        var index = 0
        var inTextObject = false
        while (index < content.length) {
            when (val ch = content[index]) {
                '(' -> {
                    val parsed = readLiteralString(content, index)
                    if (inTextObject && parsed != null) {
                        out.append(parsed.first)
                        index = parsed.second
                        continue
                    }
                    index = parsed?.second ?: (index + 1)
                }
                '<' -> {
                    if (content.getOrNull(index + 1) == '<') {
                        index += 2
                    } else {
                        val parsed = readHexString(content, index)
                        if (inTextObject && parsed != null) {
                            out.append(parsed.first)
                            index = parsed.second
                            continue
                        }
                        index = parsed?.second ?: (index + 1)
                    }
                }
                '%' -> {
                    val eol = content.indexOfAny(charArrayOf('\r', '\n'), index)
                    index = if (eol < 0) content.length else eol + 1
                }
                else -> {
                    if (ch.isLetter() || ch == '\'' || ch == '"') {
                        val tokenStart = index
                        while (index < content.length &&
                            (content[index].isLetter() || content[index] == '\'' || content[index] == '"')
                        ) {
                            index++
                        }
                        when (content.substring(tokenStart, index)) {
                            "BT" -> inTextObject = true
                            "ET" -> {
                                inTextObject = false
                                out.append('\n')
                            }
                            "Tj", "'", "\"" -> out.append(' ')
                            "TJ" -> out.append(' ')
                            "Td", "TD", "T*" -> if (inTextObject) out.append('\n')
                        }
                    } else {
                        index++
                        continue
                    }
                }
            }
        }
        return out.toString()
    }

    /** Returns decoded text and the index just past the closing parenthesis. */
    private fun readLiteralString(content: String, start: Int): Pair<String, Int>? {
        if (content.getOrNull(start) != '(') return null
        val bytes = ByteArrayOutputStream()
        var depth = 1
        var index = start + 1
        while (index < content.length) {
            val ch = content[index]
            when (ch) {
                '\\' -> {
                    index++
                    if (index >= content.length) break
                    when (val escaped = content[index]) {
                        'n' -> bytes.write('\n'.code)
                        'r' -> bytes.write('\r'.code)
                        't' -> bytes.write('\t'.code)
                        'b' -> bytes.write('\b'.code)
                        'f' -> bytes.write(12)
                        '(', ')', '\\' -> bytes.write(escaped.code)
                        '\r' -> {
                            if (content.getOrNull(index + 1) == '\n') index++
                        }
                        '\n' -> Unit
                        in '0'..'7' -> {
                            var value = escaped.digitToInt()
                            var count = 1
                            while (count < 3) {
                                val next = content.getOrNull(index + 1) ?: break
                                if (next !in '0'..'7') break
                                value = value * 8 + next.digitToInt()
                                index++
                                count++
                            }
                            bytes.write(value and 0xFF)
                        }
                        else -> bytes.write(escaped.code)
                    }
                    index++
                }
                '(' -> {
                    depth++
                    bytes.write('('.code)
                    index++
                }
                ')' -> {
                    depth--
                    if (depth == 0) {
                        return decodeBytes(bytes.toByteArray()) to (index + 1)
                    }
                    bytes.write(')'.code)
                    index++
                }
                else -> {
                    bytes.write(ch.code and 0xFF)
                    index++
                }
            }
        }
        return null
    }

    /** Returns decoded text and the index just past the closing angle bracket. */
    private fun readHexString(content: String, start: Int): Pair<String, Int>? {
        if (content.getOrNull(start) != '<') return null
        val end = content.indexOf('>', start + 1)
        if (end < 0) return null
        val digits = content.substring(start + 1, end).filter { !it.isWhitespace() }
        if (digits.isEmpty() || digits.any { it.digitToIntOrNull(16) == null }) {
            return null
        }
        val padded = if (digits.length % 2 == 0) digits else digits + "0"
        val bytes = ByteArray(padded.length / 2) { offset ->
            padded.substring(offset * 2, offset * 2 + 2).toInt(16).toByte()
        }
        return decodeBytes(bytes) to (end + 1)
    }

    private fun decodeBytes(bytes: ByteArray): String {
        if (bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
        ) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
        ) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        // Two-byte CID text without a BOM looks like mostly-zero / high-byte
        // pairs. Decoding it as UTF-16BE is the best guess; plausibility checks
        // reject it when the guess is wrong.
        if (bytes.size >= 4) {
            var zeroes = 0
            var pairs = 0
            var index = 0
            while (index + 1 < bytes.size) {
                if (bytes[index] == 0.toByte()) zeroes++
                pairs++
                index += 2
            }
            if (pairs > 0 && zeroes.toDouble() / pairs > 0.6) {
                return String(bytes, Charsets.UTF_16BE)
            }
        }
        return String(bytes, WINDOWS_1252)
    }

    private fun estimatePages(raw: String): Int {
        val count = Regex("/Type\\s*/Page(?![sA-Za-z])").findAll(raw).count()
        return if (count > 0) count else 1
    }

    private fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch == '\n' || ch == '\t' -> builder.append(ch)
                ch.isISOControl() -> builder.append(' ')
                else -> builder.append(ch)
            }
        }
        return builder.toString()
            .lineSequence()
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun isPlausible(text: String): Boolean {
        if (text.length < 8) return false
        var printable = 0
        var alphanumeric = 0
        var replacement = 0
        for (ch in text) {
            when {
                ch.isLetterOrDigit() -> {
                    printable++
                    alphanumeric++
                }
                ch.isWhitespace() -> printable++
                ch in ".,;:!?()[]{}'\"-–—/\\%&@#*+=<>|_$€¥°·、，。！？：；“”‘’（）《》【】" -> printable++
                ch == '\uFFFD' -> replacement++
                ch.isISOControl() || Character.getType(ch) == Character.UNASSIGNED.toInt() -> Unit
                else -> printable++
            }
        }
        val ratio = printable.toDouble() / text.length
        return ratio >= 0.75 &&
            alphanumeric >= 8 &&
            replacement * 20 < text.length
    }
}
