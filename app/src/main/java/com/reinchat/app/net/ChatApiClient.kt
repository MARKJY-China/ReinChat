package com.reinchat.app.net

import com.reinchat.app.data.ApiProtocol
import com.reinchat.app.data.ChatMessage
import com.reinchat.app.data.ImageStore
import com.reinchat.app.data.MessageRole
import com.reinchat.app.data.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
    data class Tool(val name: String, val status: String, val detail: String) : StreamEvent

    /**
     * Full job state replayed by the relay when the event stream is opened:
     * the text/reasoning received so far plus every tool call of the job.
     */
    data class Snapshot(
        val text: String,
        val reasoning: String,
        val tools: List<Tool>,
    ) : StreamEvent

    data object Completed : StreamEvent
}

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ChatApiClient(private val imageStore: ImageStore) {

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun stream(provider: ProviderConfig, history: List<ChatMessage>): Flow<StreamEvent> = flow {
        val endpoint = runCatching { ApiEndpoint.endpointUrl(provider.baseUrl, provider.protocol) }
            .getOrElse { throw ApiException(it.message ?: "Base URL 格式不正确") }
        val payload = buildPayload(provider, history)
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toRequestBody(JSON_MEDIA))
            .header("Accept", "text/event-stream")
            .header("User-Agent", "ReinChat/0.1 (Android)")
        if (provider.apiKey.isNotBlank()) {
            val key = normalizeApiKey(provider.apiKey)
            apiKeyHeaderProblem(key)?.let { throw ApiException(it) }
            if (key.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $key")
            }
        }

        val call = client.newCall(requestBuilder.build())
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                throw ApiException(friendlyNetworkError(e), e)
            }

            response.use {
                if (!response.isSuccessful) {
                    val raw = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    throw ApiException(httpErrorMessage(response.code, raw))
                }

                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val body = response.body ?: throw ApiException("服务商返回了空响应")

                if (contentType.contains("application/json") && !contentType.contains("event-stream")) {
                    val parsed = parseCompleteResponse(provider.protocol, body.string())
                    if (parsed.reasoning.isNotBlank()) emit(StreamEvent.Reasoning(parsed.reasoning))
                    if (parsed.content.isNotBlank()) emit(StreamEvent.Delta(parsed.content))
                    emit(StreamEvent.Completed)
                    return@use
                }
                if (contentType.contains("text/html")) {
                    throw ApiException("服务商返回了网页而不是 API 数据，请检查 Base URL")
                }

                val source = body.source()
                val parser = SseParser()
                var completed = false
                while (!completed) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    val event = parser.feedLine(line) ?: continue
                    when (val parsed = parseStreamEvent(provider.protocol, event)) {
                        null -> Unit
                        is Parsed.Reasoning -> emit(StreamEvent.Reasoning(parsed.text))
                        is Parsed.Delta -> emit(StreamEvent.Delta(parsed.text))
                        is Parsed.Both -> {
                            emit(StreamEvent.Reasoning(parsed.reasoning))
                            emit(StreamEvent.Delta(parsed.content))
                        }
                        is Parsed.Done -> completed = true
                    }
                }
                if (!completed) {
                    parser.finish()?.let { event ->
                        when (val parsed = parseStreamEvent(provider.protocol, event)) {
                            is Parsed.Reasoning -> emit(StreamEvent.Reasoning(parsed.text))
                            is Parsed.Delta -> emit(StreamEvent.Delta(parsed.text))
                            is Parsed.Both -> {
                                emit(StreamEvent.Reasoning(parsed.reasoning))
                                emit(StreamEvent.Delta(parsed.content))
                            }
                            is Parsed.Done -> completed = true
                            null -> Unit
                        }
                    }
                }
                emit(StreamEvent.Completed)
            }
        } finally {
            cancellation?.dispose()
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildPayload(provider: ProviderConfig, history: List<ChatMessage>): String {
        val model = provider.activeModel
        if (model.isBlank()) throw ApiException("请填写模型名称")
        val relevant = history.filter {
            it.content.isNotBlank() || it.images.isNotEmpty() || it.pdfText.isNotBlank()
        }
        if (relevant.isEmpty()) throw ApiException("没有可发送的消息")

        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            when (provider.protocol) {
                ApiProtocol.RESPONSES -> {
                    if (provider.systemPrompt.isNotBlank()) {
                        put("instructions", provider.systemPrompt)
                    }
                    put("input", responsesInput(relevant))
                    provider.maxTokens?.takeIf { it > 0 }?.let { put("max_output_tokens", it) }
                    if (provider.reasoningEffort.isNotBlank()) {
                        put("reasoning", buildJsonObject {
                            put("effort", provider.reasoningEffort)
                        })
                    }
                }
                ApiProtocol.CHAT_COMPLETIONS -> {
                    put("messages", chatMessages(provider, relevant))
                    provider.maxTokens?.takeIf { it > 0 }?.let { put("max_tokens", it) }
                    if (provider.reasoningEffort.isNotBlank()) {
                        put("reasoning_effort", provider.reasoningEffort)
                    }
                }
            }
            provider.temperature?.let { put("temperature", it) }
        }
        return body.toString()
    }

    private fun responsesInput(history: List<ChatMessage>): JsonArray = buildJsonArray {
        history.forEach { message ->
            add(buildJsonObject {
                put("role", if (message.role == MessageRole.USER) "user" else "assistant")
                val content = messageText(message)
                if (message.role == MessageRole.USER && message.images.isNotEmpty()) {
                    put("content", buildJsonArray {
                        if (content.isNotBlank()) {
                            add(buildJsonObject {
                                put("type", "input_text")
                                put("text", content)
                            })
                        }
                        message.images.forEach { image ->
                            add(buildJsonObject {
                                put("type", "input_image")
                                put("image_url", imageStore.dataUrl(image))
                            })
                        }
                    })
                } else {
                    put("content", content)
                }
            })
        }
    }

    private fun chatMessages(provider: ProviderConfig, history: List<ChatMessage>): JsonArray =
        buildJsonArray {
            if (provider.systemPrompt.isNotBlank()) {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", provider.systemPrompt)
                })
            }
            history.forEach { message ->
                add(buildJsonObject {
                    put("role", if (message.role == MessageRole.USER) "user" else "assistant")
                    val content = messageText(message)
                    if (message.role == MessageRole.USER && message.images.isNotEmpty()) {
                        put("content", buildJsonArray {
                            if (content.isNotBlank()) {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", content)
                                })
                            }
                            message.images.forEach { image ->
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject {
                                        put("url", imageStore.dataUrl(image))
                                    })
                                })
                            }
                        })
                    } else {
                        put("content", content)
                    }
                })
            }
        }

    /**
     * Direct-provider parity with the relay server: PDF text is inlined into the
     * message content because chat-completion APIs do not accept a PDF part.
     */
    private fun messageText(message: ChatMessage): String = directMessageText(message)

    private sealed interface Parsed {
        data class Reasoning(val text: String) : Parsed
        data class Delta(val text: String) : Parsed
        data class Both(val reasoning: String, val content: String) : Parsed
        data object Done : Parsed
    }

    private data class CompletePayload(
        val content: String,
        val reasoning: String,
    )

    private fun parseStreamEvent(protocol: ApiProtocol, event: ServerSentEvent): Parsed? {
        val data = event.data.trim()
        if (data.isEmpty()) return null
        if (data == "[DONE]") return Parsed.Done

        val root = parseObject(data) ?: return null
        root["error"]?.let { throw ApiException(errorValueMessage(it)) }

        return when (protocol) {
            ApiProtocol.CHAT_COMPLETIONS -> parseChatEvent(root)
            ApiProtocol.RESPONSES -> parseResponsesEvent(root, event.event)
        }
    }

    private fun parseChatEvent(root: JsonObject): Parsed? {
        val choice = root["choices"]?.jsonArrayOrNull()?.firstOrNull()?.jsonObjectOrNull()
            ?: return null
        val delta = choice["delta"]?.jsonObjectOrNull()
        val reasoning = (
            delta?.get("reasoning_content")
                ?: delta?.get("reasoning")
                ?: delta?.get("thinking")
            )?.toReasoningContent().orEmpty()
        val content = delta?.get("content") ?: delta?.get("text") ?: choice["text"]
        val text = content?.toTextContent().orEmpty()
        val finishReason = choice["finish_reason"]?.jsonPrimitiveOrNull()?.contentOrNull
        if (reasoning.isNotBlank() && text.isNotBlank()) return Parsed.Both(reasoning, text)
        if (reasoning.isNotBlank()) return Parsed.Reasoning(reasoning)
        if (text.isNotBlank()) return Parsed.Delta(text)
        if (finishReason != null) return Parsed.Done
        return null
    }

    private fun parseResponsesEvent(root: JsonObject, eventName: String?): Parsed? {
        val type = root["type"]?.jsonPrimitiveOrNull()?.contentOrNull ?: eventName
        return when (type) {
            "response.reasoning_summary_text.delta",
            "response.reasoning_text.delta",
            "response.reasoning_summary.delta" -> {
                val text = root["delta"]?.jsonPrimitiveOrNull()?.contentOrNull.orEmpty()
                if (text.isEmpty()) null else Parsed.Reasoning(text)
            }
            "response.output_text.delta" -> {
                val text = root["delta"]?.jsonPrimitiveOrNull()?.contentOrNull.orEmpty()
                if (text.isEmpty()) null else Parsed.Delta(text)
            }
            "response.refusal.delta" -> {
                val text = root["delta"]?.jsonPrimitiveOrNull()?.contentOrNull.orEmpty()
                if (text.isEmpty()) null else Parsed.Delta(text)
            }
            "response.completed", "response.incomplete" -> Parsed.Done
            "response.failed", "response.cancelled" -> {
                val response = root["response"]?.jsonObjectOrNull()
                val message = response?.get("error")?.let(::errorValueMessage)
                    ?: "生成失败"
                throw ApiException(message)
            }
            "error" -> throw ApiException(errorValueMessage(root["error"] ?: root))
            else -> null
        }
    }

    private fun parseCompleteResponse(protocol: ApiProtocol, raw: String): CompletePayload {
        val root = parseObject(raw) ?: throw ApiException("服务商返回了无法解析的数据")
        root["error"]?.let { throw ApiException(errorValueMessage(it)) }
        return when (protocol) {
            ApiProtocol.CHAT_COMPLETIONS -> {
                val choice = root["choices"]?.jsonArrayOrNull()?.firstOrNull()?.jsonObjectOrNull()
                val message = choice?.get("message")?.jsonObjectOrNull()
                CompletePayload(
                    content = message?.get("content")?.toTextContent()
                        ?: choice?.get("text")?.jsonPrimitiveOrNull()?.contentOrNull
                        ?: "",
                    reasoning = (
                        message?.get("reasoning_content")
                            ?: message?.get("reasoning")
                            ?: message?.get("thinking")
                        )?.toReasoningContent().orEmpty(),
                )
            }
            ApiProtocol.RESPONSES -> {
                val output = root["output"]?.jsonArrayOrNull().orEmpty()
                val content = output.mapNotNull { item ->
                    val parts = item.jsonObjectOrNull()?.get("content")?.jsonArrayOrNull()
                    parts?.mapNotNull inner@{ part ->
                        val obj = part.jsonObjectOrNull() ?: return@inner null
                        val type = obj["type"]?.jsonPrimitiveOrNull()?.contentOrNull
                        if (type == "output_text" || type == "text") {
                            obj["text"]?.jsonPrimitiveOrNull()?.contentOrNull
                        } else null
                    }?.joinToString("")
                }.joinToString("")
                val reasoning = output.mapNotNull { item ->
                    val obj = item.jsonObjectOrNull() ?: return@mapNotNull null
                    if (obj["type"]?.jsonPrimitiveOrNull()?.contentOrNull != "reasoning") return@mapNotNull null
                    obj["summary"]?.jsonArrayOrNull()?.mapNotNull { part ->
                        part.jsonObjectOrNull()?.get("text")?.jsonPrimitiveOrNull()?.contentOrNull
                    }?.joinToString("")
                }.joinToString("\n")
                CompletePayload(content, reasoning)
            }
        }
    }

    private fun JsonElement.toReasoningContent(): String = when (this) {
        is JsonObject -> get("content")?.toTextContent()
            ?: get("text")?.toTextContent()
            ?: get("reasoning")?.toTextContent()
            ?: ""
        else -> toTextContent()
    }

    private fun JsonElement.toTextContent(): String = when (this) {
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonArray -> mapNotNull { part ->
            val obj = part.jsonObjectOrNull() ?: return@mapNotNull null
            val type = obj["type"]?.jsonPrimitiveOrNull()?.contentOrNull
            if (type == "text" || type == "output_text" || type == "input_text") {
                obj["text"]?.jsonPrimitiveOrNull()?.contentOrNull
            } else null
        }.joinToString("")
        else -> ""
    }

    private fun errorValueMessage(value: JsonElement): String {
        return when (value) {
            is JsonPrimitive -> value.contentOrNull.orEmpty()
            is JsonObject -> {
                value["message"]?.jsonPrimitiveOrNull()?.contentOrNull
                    ?: value["error"]?.let(::errorValueMessage)
                    ?: value["detail"]?.jsonPrimitiveOrNull()?.contentOrNull
                    ?: value.toString()
            }
            is JsonArray -> value.joinToString(" ") { errorValueMessage(it) }
            else -> value.toString()
        }.ifBlank { "服务商返回了错误" }
    }

    private fun httpErrorMessage(code: Int, raw: String): String {
        val detail = parseObject(raw)?.let { root ->
            listOf("error", "message", "detail")
                .firstNotNullOfOrNull { key ->
                    root[key]?.let { value ->
                        errorValueMessage(value).takeIf { it.isNotBlank() }
                    }
                }
        } ?: raw.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
            .take(220).ifBlank { null }

        val prefix = when (code) {
            401, 403 -> "API Key 无效或没有权限（HTTP $code）"
            404 -> "接口不存在，请检查 Base URL（HTTP 404）"
            429 -> "请求过于频繁或额度不足（HTTP 429）"
            in 500..599 -> "服务商暂时不可用（HTTP $code）"
            else -> "请求失败（HTTP $code）"
        }
        return if (detail.isNullOrBlank()) prefix else "$prefix\n$detail"
    }

    private fun parseObject(raw: String): JsonObject? = runCatching {
        json.parseToJsonElement(raw).jsonObjectOrNull()
    }.getOrNull()

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive
}

/**
 * Direct-provider parity with the relay server: PDF text is inlined into the
 * message content because chat-completion APIs do not accept a PDF part.
 */
internal fun directMessageText(message: ChatMessage): String {
    if (message.pdfText.isBlank()) return message.content
    val name = message.pdfName.ifBlank { "文档.pdf" }
    val base = message.content.trimEnd()
    return if (base.isBlank()) {
        "[附件 PDF：$name]\n${message.pdfText}"
    } else {
        "$base\n\n[附件 PDF：$name]\n${message.pdfText}"
    }
}

internal fun friendlyNetworkError(error: Throwable): String = when (error) {    is UnknownHostException -> "无法连接服务器，请检查 Base URL 和网络"
    is ConnectException -> "连接被拒绝，请检查 Base URL、端口或服务是否已启动"
    is NoRouteToHostException -> "无法路由到服务器，请检查网络连接"
    is SocketTimeoutException -> "连接超时，请稍后重试"
    is SSLException -> "TLS 连接失败，请检查服务器证书或改用正确的 https 地址"
    else -> {
        val message = error.message.orEmpty()
        if (error is IOException) "网络请求失败${if (message.isBlank()) "" else "：$message"}"
        else message.ifBlank { "发生未知错误" }
    }
}
