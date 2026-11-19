package com.reinchat.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

class PdfTextExtractorTest {

    private fun pdfWith(content: String, flate: Boolean = false): ByteArray {
        val streamBytes = if (flate) deflate(content) else content.toByteArray(Charsets.ISO_8859_1)
        val filter = if (flate) "/Filter /FlateDecode " else ""
        val head = "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /Page /Contents 2 0 R >>\nendobj\n" +
            "2 0 obj\n<< $filter/Length ${streamBytes.size} >>\nstream\n"
        val bytes = ByteArrayOutputStream()
        bytes.write(head.toByteArray(Charsets.ISO_8859_1))
        bytes.write(streamBytes)
        bytes.write(
            "\nendstream\nendobj\ntrailer\n<<>>\n%%EOF\n"
                .toByteArray(Charsets.ISO_8859_1)
        )
        return bytes.toByteArray()
    }

    private fun deflate(text: String): ByteArray {
        val raw = text.toByteArray(Charsets.ISO_8859_1)
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) {
            val read = deflater.deflate(buffer)
            output.write(buffer, 0, read)
        }
        deflater.end()
        return output.toByteArray()
    }

    @Test
    fun extractsTextFromUncompressedContentStream() {
        val result = PdfTextExtractor.extract(
            pdfWith("BT /F1 12 Tf 72 720 Td (Hello ReinChat PDF) Tj ET")
        )
        assertTrue(result.hasText)
        assertTrue(result.text.contains("Hello ReinChat PDF"))
        assertTrue(result.pages >= 1)
    }

    @Test
    fun extractsTextFromFlateCompressedContentStream() {
        val result = PdfTextExtractor.extract(
            pdfWith("BT /F1 12 Tf (Compressed document text) Tj ET", flate = true)
        )
        assertTrue(result.hasText)
        assertTrue(result.text.contains("Compressed document text"))
    }

    @Test
    fun rejectsCidGarbageSoCallerFallsBackToOcr() {
        // Identity-H CID strings have no ToUnicode mapping in this minimal
        // extractor; the result must be empty so the OCR fallback runs.
        val result = PdfTextExtractor.extract(
            pdfWith("BT /F1 12 Tf <0001000200030004000500060007> Tj ET")
        )
        assertFalse(result.hasText)
    }

    @Test
    fun ignoresNonPdfPayload() {
        val result = PdfTextExtractor.extract("not a pdf at all".toByteArray())
        assertFalse(result.hasText)
    }
}
