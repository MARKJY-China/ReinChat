package com.reinchat.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
}

@Serializable
enum class MessageStatus {
    COMPLETE,
    STREAMING,
    STOPPED,
    ERROR,
}

@Serializable
enum class ApiProtocol {
    RESPONSES,
    CHAT_COMPLETIONS,
}

@Serializable
data class ChatImage(
    val id: String = newId(),
    val path: String,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
    val remoteId: String? = null,
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

@Serializable
data class ChatMessage(
    val id: String = newId(),
    val role: MessageRole,
    val content: String = "",
    /** Provider reasoning/thinking text, kept separate from the visible answer. */
    val reasoning: String = "",
    val images: List<ChatImage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val error: String? = null,
)

@Serializable
data class Conversation(
    val id: String = newId(),
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    /** Set once at creation. A cloud conversation can never become local. */
    val cloud: Boolean = false,
    val cloudVersion: Long = 0,
) {
    val preview: String
        get() {
            val last = messages.lastOrNull { it.content.isNotBlank() || it.images.isNotEmpty() }
                ?: return "还没有消息"
            if (last.content.isBlank()) return "图片 × ${last.images.size}"
            return last.content
                .lineSequence()
                .map { line ->
                    line.trim()
                        .replace(Regex("^#{1,6}\\s*"), "")
                        .replace(Regex("^[-*+]\\s+"), "")
                        .replace(Regex("^\\d+[.)]\\s+"), "")
                        .replace(Regex("^>\\s*"), "")
                }
                .filter { it.isNotBlank() && !it.startsWith("```") }
                .joinToString(" ")
                .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
                .replace(Regex("#{1,6}\\s+"), "")
                .replace(Regex("[`*_~]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "还没有消息" }
        }

    fun withMessages(updated: List<ChatMessage>): Conversation = copy(
        messages = updated,
        updatedAt = System.currentTimeMillis(),
    )
}

@Serializable
data class ProviderConfig(
    val id: String = newId(),
    val name: String = "OpenAI",
    val protocol: ApiProtocol = ApiProtocol.CHAT_COMPLETIONS,
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val systemPrompt: String = "",
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    /** Local selection; model itself may contain a comma-separated list. */
    val selectedModel: String = "",
    /** "", "low", "high" or "max"; sent as reasoning_effort when set. */
    val reasoningEffort: String = "high",
    /** Local-only providers are never uploaded to the account. */
    val localOnly: Boolean = true,
    /** True after the cloud copy is known to match this local copy. */
    val cloudSynced: Boolean = false,
) {
    val modelOptions: List<String>
        get() = modelOptionsOf(model)

    val activeModel: String
        get() = selectedModel.trim().takeIf { it in modelOptions }
            ?: modelOptions.firstOrNull().orEmpty()

    val endpointLabel: String
        get() = when (protocol) {
            ApiProtocol.RESPONSES -> "Responses"
            ApiProtocol.CHAT_COMPLETIONS -> "Chat Completions"
        }
}

fun modelOptionsOf(raw: String): List<String> = raw
    .split(',')
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()

const val DEFAULT_CLOUD_SERVER = "https://reinchat.markjy.com"

@Serializable
data class CloudAccount(
    val username: String,
    val serverUrl: String,
    val token: String,
    val expiresAt: Long,
    val level: Int = 1,
)

@Serializable
data class AppSettings(
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String? = null,
    val cloudAccount: CloudAccount? = null,
    val cloudServerUrl: String = DEFAULT_CLOUD_SERVER,
    val pendingCloudConversationDeletes: List<String> = emptyList(),
    val pendingCloudProviderDeletes: List<String> = emptyList(),
) {
    val activeProvider: ProviderConfig?
        get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()
}

fun titleFromPrompt(text: String, imageCount: Int): String {
    val compact = text.replace(Regex("\\s+"), " ").trim()
    if (compact.isBlank()) return if (imageCount > 0) "图片对话" else "新对话"
    return if (compact.length <= 30) compact else compact.take(29).trimEnd() + "…"
}

fun friendlyError(raw: String?): String {
    val message = raw?.trim().orEmpty()
    if (message.isBlank()) return "请求失败，请检查服务商配置后重试"
    return message.take(600)
}
