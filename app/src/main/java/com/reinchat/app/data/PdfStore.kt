package com.reinchat.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class PdfImportException(message: String) : Exception(message)

/**
 * Copies a picked/pasted PDF content URI into the app cache so extraction and
 * the server OCR upload can read a stable local file. Content is always read
 * through [android.content.ContentResolver][Context.getContentResolver], which
 * also works for clipboard and DocumentsProvider URIs.
 */
class PdfStore(private val context: Context) {

    companion object {
        private const val TAG = "PdfStore"
        const val MAX_PDF_BYTES = 40L * 1024 * 1024
        private const val MAX_NAME_CHARS = 120
    }

    data class StoredPdf(
        val file: File,
        val name: String,
        val mimeType: String,
        val size: Long,
    )

    private val pdfDir: File
        get() = File(context.cacheDir, "pdf").apply { mkdirs() }

    suspend fun import(uri: Uri): StoredPdf = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
        val displayName = queryDisplayName(uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "文档.pdf"
        val safeName = sanitizeName(displayName)
        if (mime.isNotBlank() &&
            mime.lowercase() != "application/pdf" &&
            !mime.lowercase().contains("pdf")
        ) {
            throw PdfImportException("请选择 PDF 文件")
        }
        if (!safeName.lowercase().endsWith(".pdf") && !mime.lowercase().contains("pdf")) {
            throw PdfImportException("请选择 PDF 文件")
        }

        val id = newId()
        val target = File(pdfDir, "$id.pdf")
        val temp = File(pdfDir, "$id.pdf.part")
        temp.delete()
        try {
            val stream = resolver.openInputStream(uri)
                ?: throw PdfImportException("无法读取这个 PDF")
            stream.use { input ->
                temp.outputStream().use { output ->
                    val copied = copyLimited(input, output)
                    if (copied <= 0) throw PdfImportException("PDF 文件为空")
                    if (copied > MAX_PDF_BYTES) {
                        throw PdfImportException("PDF 不能超过 40MB")
                    }
                }
            }
            if (!looksLikePdf(temp)) {
                throw PdfImportException("这个文件不是有效的 PDF")
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            StoredPdf(
                file = target,
                name = safeName,
                mimeType = "application/pdf",
                size = target.length(),
            )
        } catch (e: Throwable) {
            temp.delete()
            if (e !is PdfImportException) {
                Log.w(TAG, "PDF import failed", e)
                throw PdfImportException("PDF 读取失败，请重试")
            }
            throw e
        }
    }

    fun delete(path: String) {
        if (path.isBlank()) return
        runCatching {
            val file = File(path)
            // Only ever delete files this store created.
            if (file.parentFile == pdfDir) file.delete()
        }
    }

    private fun copyLimited(input: InputStream, output: java.io.OutputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_PDF_BYTES) throw PdfImportException("PDF 不能超过 40MB")
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun looksLikePdf(file: File): Boolean {
        if (file.length() < 8) return false
        val header = ByteArray(1024)
        val read = file.inputStream().use { input -> input.read(header) }
        if (read <= 0) return false
        // The %PDF- marker may follow a small amount of junk/BOM, so look
        // inside the first KiB instead of requiring byte zero.
        return String(header, 0, read, Charsets.ISO_8859_1).contains("%PDF-")
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    }.getOrNull()

    private fun sanitizeName(raw: String): String {
        val trimmed = raw.trim().replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
        val bounded = if (trimmed.length <= MAX_NAME_CHARS) trimmed else trimmed.take(MAX_NAME_CHARS)
        if (bounded.isBlank()) return "文档.pdf"
        return if (bounded.lowercase().endsWith(".pdf")) bounded else "$bounded.pdf"
    }
}
