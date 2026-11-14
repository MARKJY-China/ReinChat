package com.reinchat.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AppUpdate(
    val latestVersion: String,
    val mandatory: Boolean,
    val downloadUrl: String,
    val notes: String = "",
)

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateDownloadState

    data class Ready(val filePath: String) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

/**
 * Fetches the Cloudreve-hosted manifest. The format is intentionally tiny:
 *
 * [LatestVersion]
 * 0.4.1
 * [MandatoryUpdate]
 * Yes
 * [DownloadURL]
 * https://...
 */
object UpdateChecker {
    const val MANIFEST_URL = "https://pan.szcmate.cn/f/x9rfm/version.txt"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(MANIFEST_URL)
            .header("User-Agent", "ReinChat-Android/$currentVersion")
            .header("Accept", "text/plain")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string()?.take(64 * 1024) ?: return@withContext null
            parse(body, currentVersion)
        }
    }

    fun parse(raw: String, currentVersion: String): AppUpdate? {
        val sections = mutableMapOf<String, StringBuilder>()
        var section: String? = null
        for (line in raw.lineSequence()) {
            val trimmed = line.trim().removePrefix("\uFEFF")
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed.substring(1, trimmed.length - 1).trim().lowercase()
                sections.getOrPut(section) { StringBuilder() }
                continue
            }
            val key = section ?: continue
            sections.getOrPut(key) { StringBuilder() }.append(line).append('\n')
        }
        val latest = sections["latestversion"]?.toString()?.lineSequence()
            ?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }.orEmpty()
        val downloadUrl = sections["downloadurl"]?.toString()?.lineSequence()
            ?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }.orEmpty()
        if (latest.isEmpty() || !downloadUrl.startsWith("https://")) {
            return null
        }
        if (!isNewer(latest, currentVersion)) return null
        val mandatory = sections["mandatoryupdate"]?.toString()?.lineSequence()
            ?.map { it.trim().lowercase() }?.firstOrNull { it.isNotEmpty() }
            .let { it in setOf("yes", "true", "1") }
        val notes = sections["updatenotes"]?.toString()?.trim().orEmpty()
        return AppUpdate(
            latestVersion = latest,
            mandatory = mandatory,
            downloadUrl = downloadUrl,
            notes = notes,
        )
    }

    internal fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = versionParts(remote)
        val currentParts = versionParts(current)
        if (remoteParts.isEmpty() || currentParts.isEmpty()) return false
        val count = maxOf(remoteParts.size, currentParts.size)
        for (index in 0 until count) {
            val left = remoteParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun versionParts(raw: String): List<Int> =
        Regex("""\d+""").findAll(raw).mapNotNull { it.value.toIntOrNull() }.toList()
}
