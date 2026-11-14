package com.reinchat.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Base64

class ImageImportException(message: String) : Exception(message)

class ImageStore(private val context: Context) {

    companion object {
        private const val TAG = "ImageStore"
        private const val MAX_SOURCE_BYTES = 28 * 1024 * 1024
        private const val MAX_EDGE = 1600
        private const val JPEG_QUALITY = 88
    }

    private val imageDir: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    suspend fun import(uri: Uri): ChatImage = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime.isNotBlank() && !mime.startsWith("image/")) {
            throw ImageImportException("请选择图片文件")
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readLimited() }
            ?: throw ImageImportException("无法读取这张图片")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ImageImportException("图片格式不受支持")
        }

        val sample = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: throw ImageImportException("图片解码失败")

        val oriented = applyExifOrientation(decoded, readOrientation(bytes))
        val scaled = scaleWithin(oriented, MAX_EDGE)
        val flattened = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }

        val id = newId()
        val file = File(imageDir, "$id.jpg")
        file.outputStream().use { output ->
            if (!flattened.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                throw ImageImportException("图片保存失败")
            }
        }

        val result = ChatImage(
            id = id,
            path = file.absolutePath,
            mimeType = "image/jpeg",
            width = flattened.width,
            height = flattened.height,
        )

        listOf(decoded, oriented, scaled, flattened)
            .distinct()
            .filterNot { it.isRecycled }
            .forEach { it.recycle() }
        result
    }

    fun delete(image: ChatImage) {
        runCatching { File(image.path).takeIf { it.isFile }?.delete() }
            .onFailure { Log.w(TAG, "Could not delete image", it) }
    }

    fun deleteAll(images: List<ChatImage>) {
        images.forEach(::delete)
    }

    fun dataUrl(image: ChatImage): String {
        val bytes = File(image.path).readBytes()
        return "data:${image.mimeType};base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    private fun InputStream.readLimited(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_SOURCE_BYTES) {
                throw ImageImportException("图片超过 28 MB，请先压缩后再试")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= target) {
            sample *= 2
            longest /= 2
        }
        return sample
    }

    private fun readOrientation(bytes: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun scaleWithin(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
