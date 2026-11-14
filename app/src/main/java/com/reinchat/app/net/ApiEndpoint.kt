package com.reinchat.app.net

import com.reinchat.app.data.ApiProtocol
import java.net.URI

object ApiEndpoint {

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().ifBlank { throw IllegalArgumentException("请填写 Base URL") }
        val withScheme = when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            trimmed.contains("://") -> throw IllegalArgumentException("Base URL 仅支持 http 或 https")
            else -> "https://$trimmed"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull()
            ?: throw IllegalArgumentException("Base URL 格式不正确")
        if (uri.scheme !in listOf("http", "https")) {
            throw IllegalArgumentException("Base URL 仅支持 http 或 https")
        }
        if (uri.host.isNullOrBlank()) throw IllegalArgumentException("Base URL 缺少域名")
        return withScheme.trimEnd('/')
    }

    fun endpointUrl(baseUrl: String, protocol: ApiProtocol): String {
        val base = normalizeBaseUrl(baseUrl)
        val suffix = when (protocol) {
            ApiProtocol.RESPONSES -> "/responses"
            ApiProtocol.CHAT_COMPLETIONS -> "/chat/completions"
        }
        return if (base.endsWith(suffix)) base else base + suffix
    }

    fun validationError(baseUrl: String): String? = runCatching {
        normalizeBaseUrl(baseUrl)
        null
    }.getOrElse { it.message ?: "Base URL 格式不正确" }
}
