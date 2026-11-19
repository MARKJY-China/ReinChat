package com.reinchat.app.net

import com.reinchat.app.data.ChatImage
import com.reinchat.app.data.ChatMessage
import com.reinchat.app.data.CloudAccount
import com.reinchat.app.data.Conversation
import com.reinchat.app.data.MessageRole
import com.reinchat.app.data.MessageSegment
import com.reinchat.app.data.MessageStatus
import com.reinchat.app.data.ProviderConfig
import com.reinchat.app.data.ProviderModel
import com.reinchat.app.data.ApiProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

open class CloudApiException(
    val code: String?,
    message: String,
    val httpStatus: Int? = null,
) : IOException(message)

class CloudAuthException(message: String = "登录已过期，请重新登录") :
    CloudApiException("unauthorized", message, 401)

class RelayCancelledException(message: String = "生成已取消") :
    CloudApiException("cancelled", message)

class CloudConflictException(
    message: String,
    val remoteConversation: ConversationDto,
) : CloudApiException("version_conflict", message, 409)

object CloudEndpoint {
    fun normalize(raw: String): String {
        val trimmed = raw.trim().ifBlank { throw IllegalArgumentException("请填写服务器地址") }
        val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val url = withScheme.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("服务器地址格式不正确")
        if (url.scheme != "http" && url.scheme != "https") {
            throw IllegalArgumentException("服务器地址仅支持 http/https")
        }
        val path = url.encodedPath.trimEnd('/')
        val basePath = if (path.endsWith("/api/v1")) path else "$path/api/v1"
        return url.newBuilder()
            .encodedPath(basePath)
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
    }

    fun validate(raw: String): String? = runCatching {
        normalize(raw)
        null
    }.getOrElse { it.message ?: "服务器地址格式不正确" }
}

@Serializable
data class CloudUserDto(val username: String, val level: Int = 1)

@Serializable
data class CloudAuthDto(
    val token: String,
    val expiresAt: Long,
    val user: CloudUserDto,
)

@Serializable
data class ProviderModelDto(
    val id: String = "",
    val name: String = "",
    val contextWindow: Int = 0,
    val maxOutputTokens: Int = 0,
    val reasoning: Boolean = false,
)

@Serializable
data class ProviderDto(
    val id: String,
    val name: String = "",
    val protocol: String = "CHAT_COMPLETIONS",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val models: List<ProviderModelDto> = emptyList(),
    val systemPrompt: String = "",
    val temperature: Double? = null,
    val maxTokens: Int? = null,
)

@Serializable
data class CloudImageRefDto(
    val id: String,
    val remoteId: String? = null,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class MessageSegmentDto(
    val type: String,
    val text: String = "",
    val name: String = "",
    val label: String = "",
    val status: String = "",
    val detail: String = "",
)

@Serializable
data class CloudMessageDto(
    val id: String,
    val role: String,
    val content: String = "",
    val reasoning: String = "",
    val pdfName: String = "",
    val pdfText: String = "",
    val segments: List<MessageSegmentDto> = emptyList(),
    val model: String = "",
    val images: List<CloudImageRefDto> = emptyList(),
    val createdAt: Long = 0,
    val status: String = "COMPLETE",
    val error: String? = null,
)

@Serializable
data class ConversationDto(
    val id: String,
    val title: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val version: Long = 0,
    val messages: List<CloudMessageDto> = emptyList(),
)

@Serializable
data class ConversationSummaryDto(
    val id: String,
    val title: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val version: Long = 0,
    val messageCount: Int = 0,
    val preview: String = "",
)

@Serializable
data class ConversationListDto(val conversations: List<ConversationSummaryDto> = emptyList())

@Serializable
data class ProviderListDto(val providers: List<ProviderDto> = emptyList())

@Serializable
data class RelayProviderDto(
    val id: String,
    val name: String = "",
    val protocol: String = "",
    val model: String = "",
    val models: List<ProviderModelDto> = emptyList(),
)

@Serializable
data class RelayProviderListDto(val providers: List<RelayProviderDto> = emptyList())

@Serializable
data class PdfOcrDto(
    val text: String = "",
    val pages: Int = 0,
)

@Serializable
data class CloudSyncEventDto(
    val type: String = "",
    val conversationId: String = "",
    val version: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class UpdateConversationRequest(
    val baseVersion: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<CloudMessageDto>,
)

@Serializable
data class CloudFileDto(
    val id: String,
    val mimeType: String = "image/jpeg",
    val size: Long = 0,
)

@Serializable
private data class RelayRequestDto(
    val providerId: String,
    val conversationId: String,
    val model: String,
    val reasoningEffort: String,
    val search: Boolean = false,
    val messages: List<CloudMessageDto>,
)

@Serializable
private data class RelayJobDto(val id: String = "", val status: String = "")

@Serializable
private data class ConversationTitleRequest(
    val providerId: String,
    val model: String,
    val text: String,
)

@Serializable
private data class ConversationTitleResponse(val title: String = "")

@Serializable
private data class ErrorBody(val code: String? = null, val message: String? = null)

@Serializable
private data class ErrorEnvelope(val error: ErrorBody? = null, val conversation: ConversationDto? = null)

class CloudApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val relayParser = RelayEventParser(json)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // Relay event streams run for as long as the model generates. An overall
        // call timeout here would kill long answers mid-stream; idle connections
        // are already covered by readTimeout plus server keep-alive pings.
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /** PDF OCR can take minutes on the server; it needs its own generous budget. */
    private val ocrClient = client.newBuilder()
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(220, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    suspend fun register(serverUrl: String, username: String, password: String): CloudAuthDto =
        authCall(serverUrl, "register", username, password)

    suspend fun login(serverUrl: String, username: String, password: String): CloudAuthDto =
        authCall(serverUrl, "login", username, password)

    private suspend fun authCall(
        serverUrl: String,
        action: String,
        username: String,
        password: String,
    ): CloudAuthDto = withContext(Dispatchers.IO) {
        val base = baseUrl(serverUrl)
        val body = json.encodeToString(
            AuthRequest(username = username.trim(), password = password)
        )
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegments("auth/$action").build())
            .post(body.toRequestBody(JSON))
            .build()
        executeJson(request) { json.decodeFromString<CloudAuthDto>(it) }
    }

    suspend fun me(account: CloudAccount): CloudUserDto = withContext(Dispatchers.IO) {
        val request = authorized(account, "auth/me").get().build()
        executeJson(request) { json.decodeFromString<MeResponse>(it).user }
    }

    suspend fun logout(account: CloudAccount) = withContext(Dispatchers.IO) {
        val request = authorized(account, "auth/logout").post(EMPTY).build()
        executeNoContent(request)
    }

    fun events(account: CloudAccount): Flow<CloudSyncEventDto> = flow {
        val request = authorized(account, "events")
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val call = client.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw errorFor(response.code, response.body?.string().orEmpty())
                }
                val source = response.body?.source()
                    ?: throw CloudApiException("bad_response", "云端没有返回事件流")
                val data = StringBuilder()
                while (currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        if (data.isNotEmpty()) {
                            val event = runCatching {
                                json.decodeFromString<CloudSyncEventDto>(data.toString())
                            }.getOrNull()
                            data.clear()
                            if (event != null) emit(event)
                        }
                    } else if (line.startsWith("data:")) {
                        data.append(line.removePrefix("data:").trim())
                    }
                }
            }
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    fun relay(
        account: CloudAccount,
        providerId: String,
        conversationId: String,
        model: String,
        reasoningEffort: String,
        search: Boolean,
        messages: List<ChatMessage>,
        onJobStarted: ((String) -> Unit)? = null,
    ): Flow<StreamEvent> = flow {
        val payload = RelayRequestDto(
            providerId = providerId,
            conversationId = conversationId,
            model = model,
            reasoningEffort = reasoningEffort,
            search = search,
            messages = messages.map(::relayMessageDto),
        )
        val request = authorized(account, "relay/jobs")
            .post(json.encodeToString(payload).toRequestBody(JSON))
            .build()
        val job = executeJson(request) { json.decodeFromString<RelayJobDto>(it) }
        if (job.id.isBlank()) throw CloudApiException("relay_error", "服务端没有返回任务 ID")
        onJobStarted?.invoke(job.id)
        val eventRequest = authorized(account, "relay/jobs/${job.id}/events")
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val call = client.newCall(eventRequest)
        // Cancelling the collecting coroutine (stop button, screen clear) must
        // abort the blocking SSE read; call.cancel() in finally alone would run
        // only after readUtf8Line returned.
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw errorFor(response.code, response.body?.string().orEmpty())
                }
                val source = response.body?.source()
                    ?: throw CloudApiException("bad_response", "服务端没有返回中转事件流")
                val data = StringBuilder()
                while (currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        if (data.isNotEmpty()) {
                            val frame = relayParser.parse(data.toString())
                            data.clear()
                            when (frame) {
                                null -> Unit
                                is RelayFrame.Events -> {
                                    frame.events.forEach { emit(it) }
                                    if (frame.terminal) {
                                        emit(StreamEvent.Completed)
                                        return@flow
                                    }
                                }
                                RelayFrame.Finished -> {
                                    emit(StreamEvent.Completed)
                                    return@flow
                                }
                                RelayFrame.Cancelled -> throw RelayCancelledException()
                                is RelayFrame.Failed -> throw CloudApiException(
                                    "relay_error",
                                    frame.message,
                                )
                            }
                        }
                    } else if (line.startsWith("data:")) {
                        data.append(line.removePrefix("data:").trim())
                    }
                }
            }
        } finally {
            cancellation?.dispose()
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun listProviders(account: CloudAccount): List<ProviderDto> = withContext(Dispatchers.IO) {
        val request = authorized(account, "providers").get().build()
        executeJson(request) { json.decodeFromString<ProviderListDto>(it).providers }
    }

    suspend fun putProvider(account: CloudAccount, provider: ProviderDto): ProviderDto =
        withContext(Dispatchers.IO) {
            val request = authorized(account, "providers/${provider.id}")
                .put(json.encodeToString(provider).toRequestBody(JSON))
                .build()
            executeJson(request) { json.decodeFromString<ProviderDto>(it) }
        }

    suspend fun deleteProvider(account: CloudAccount, id: String) = withContext(Dispatchers.IO) {
        val request = authorized(account, "providers/$id").delete().build()
        executeNoContent(request)
    }

    suspend fun listConversations(account: CloudAccount): List<ConversationSummaryDto> =
        withContext(Dispatchers.IO) {
            val request = authorized(account, "conversations").get().build()
            executeJson(request) { json.decodeFromString<ConversationListDto>(it).conversations }
        }

    suspend fun getConversation(account: CloudAccount, id: String): ConversationDto =
        withContext(Dispatchers.IO) {
            val request = authorized(account, "conversations/$id").get().build()
            executeJson(request) { json.decodeFromString<ConversationDto>(it) }
        }

    suspend fun createConversation(account: CloudAccount, conversation: Conversation): ConversationDto =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(toDto(conversation))
            val request = authorized(account, "conversations")
                .post(body.toRequestBody(JSON))
                .build()
            executeJson(request) { json.decodeFromString<ConversationDto>(it) }
        }

    suspend fun updateConversation(
        account: CloudAccount,
        conversation: Conversation,
        baseVersion: Long,
    ): ConversationDto = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            UpdateConversationRequest(
                baseVersion = baseVersion,
                title = conversation.title,
                createdAt = conversation.createdAt,
                updatedAt = conversation.updatedAt,
                messages = conversation.messages.map(::messageDto),
            )
        )
        val request = authorized(account, "conversations/${conversation.id}")
            .put(body.toRequestBody(JSON))
            .build()
        executeJson(request) { json.decodeFromString<ConversationDto>(it) }
    }

    suspend fun deleteConversation(account: CloudAccount, id: String) = withContext(Dispatchers.IO) {
        val request = authorized(account, "conversations/$id").delete().build()
        executeNoContent(request)
    }

    suspend fun uploadImage(account: CloudAccount, image: ChatImage): CloudFileDto =
        withContext(Dispatchers.IO) {
            val file = File(image.path)
            if (!file.isFile) throw CloudApiException("image_missing", "本地图片不存在，无法上传")
            val media = image.mimeType.toMediaType()
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody(media))
                .build()
            val request = authorized(account, "files").post(multipart).build()
            executeJson(request) { json.decodeFromString<CloudFileDto>(it) }
        }

    suspend fun downloadImage(account: CloudAccount, remoteId: String, destination: File) =
        withContext(Dispatchers.IO) {
            val request = authorized(account, "files/$remoteId").get().build()
            val response = client.newCall(request).execute()
            response.use {
                if (!response.isSuccessful) throw errorFor(response.code, response.body?.string().orEmpty())
                val body = response.body ?: throw CloudApiException("empty_response", "图片下载失败")
                destination.parentFile?.mkdirs()
                val temp = File(destination.parentFile, destination.name + ".tmp")
                body.byteStream().use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!temp.renameTo(destination)) {
                    temp.copyTo(destination, overwrite = true)
                    temp.delete()
                }
            }
        }

    suspend fun listRelayProviders(account: CloudAccount): List<RelayProviderDto> =
        withContext(Dispatchers.IO) {
            val request = authorized(account, "relay/providers").get().build()
            executeJson(request) { json.decodeFromString<RelayProviderListDto>(it).providers }
        }

    /**
     * Server-side OCR for PDFs without a text layer (multipart `file` field).
     * The server renders pages with pdftoppm and runs tesseract; it can take a
     * few minutes, so this call uses the generous [ocrClient] budget.
     */
    suspend fun ocrPdf(
        account: CloudAccount,
        file: File,
        mimeType: String = "application/pdf",
    ): PdfOcrDto = withContext(Dispatchers.IO) {
        if (!file.isFile) throw CloudApiException("pdf_missing", "本地 PDF 不存在，无法识别")
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mimeType.toMediaType()))
            .build()
        val request = authorized(account, "pdf/ocr").post(multipart).build()
        // Surface transport failures as a cloud error so the composer can say
        // "OCR failed, attachment kept" instead of a generic parse failure.
        val response = try {
            ocrClient.newCall(request).execute()
        } catch (e: IOException) {
            throw CloudApiException("ocr_network", friendlyNetworkError(e))
        }
        response.use {
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw errorFor(response.code, text)
            runCatching { json.decodeFromString<PdfOcrDto>(text) }.getOrElse {
                throw CloudApiException("bad_response", "服务器返回了无法解析的 OCR 数据")
            }
        }
    }

    /**
     * Asks the server to cancel a running relay job. A job that already ended
     * returns 404/409; both are treated as success because the goal (no more
     * generation) is already satisfied.
     */
    suspend fun cancelRelayJob(account: CloudAccount, jobId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (jobId.isBlank()) return@withContext false
            val request = authorized(account, "relay/jobs/$jobId/cancel")
                .post(EMPTY)
                .build()
            val response = client.newCall(request).execute()
            response.use {
                when {
                    response.isSuccessful -> true
                    response.code == 404 || response.code == 409 -> true
                    else -> throw errorFor(response.code, response.body?.string().orEmpty())
                }
            }
        }

    /** Generates a short conversation title from the first user message. */
    suspend fun generateConversationTitle(
        account: CloudAccount,
        conversationId: String,
        providerId: String,
        model: String,
        text: String,
    ): String = withContext(Dispatchers.IO) {
        if (conversationId.isBlank() || providerId.isBlank() || text.isBlank()) {
            return@withContext ""
        }
        val body = json.encodeToString(
            ConversationTitleRequest(
                providerId = providerId,
                model = model,
                text = text.take(4000),
            )
        )
        val request = authorized(account, "conversations/$conversationId/title")
            .post(body.toRequestBody(JSON))
            .build()
        executeJson(request) {
            json.decodeFromString<ConversationTitleResponse>(it).title.trim()
        }
    }

    private fun baseUrl(serverUrl: String): HttpUrl =
        CloudEndpoint.normalize(serverUrl).toHttpUrlOrNull()
            ?: throw CloudApiException("bad_server", "服务器地址格式不正确")

    private fun authorized(account: CloudAccount, path: String): Request.Builder {
        val base = baseUrl(account.serverUrl)
        return Request.Builder()
            .url(base.newBuilder().addPathSegments(path).build())
            .header("Authorization", "Bearer ${account.token}")
            .header("Accept", "application/json")
    }

    private fun executeNoContent(request: Request) {
        val response = client.newCall(request).execute()
        response.use {
            if (!response.isSuccessful) throw errorFor(response.code, response.body?.string().orEmpty())
        }
    }

    private inline fun <T> executeJson(request: Request, parse: (String) -> T): T {
        val response = client.newCall(request).execute()
        response.use {
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw errorFor(response.code, text)
            return runCatching { parse(text) }.getOrElse {
                throw CloudApiException("bad_response", "服务器返回了无法解析的数据")
            }
        }
    }

    private fun errorFor(code: Int, raw: String): Throwable {
        val envelope = runCatching { json.decodeFromString<ErrorEnvelope>(raw) }.getOrNull()
        val message = envelope?.error?.message?.takeIf { it.isNotBlank() }
            ?: when (code) {
                401, 403 -> "登录已过期，请重新登录"
                429 -> "请求过于频繁，请稍后再试"
                in 500..599 -> "云端服务暂时不可用（HTTP $code）"
                else -> "云端请求失败（HTTP $code）"
            }
        if (code == 409 && envelope?.conversation != null) {
            return CloudConflictException(message, envelope.conversation)
        }
        if (code == 401) return CloudAuthException(message)
        return CloudApiException(envelope?.error?.code, message, code)
    }

    private fun toDto(conversation: Conversation): ConversationDto = ConversationDto(
        id = conversation.id,
        title = conversation.title,
        createdAt = conversation.createdAt,
        updatedAt = conversation.updatedAt,
        version = conversation.cloudVersion,
        messages = conversation.messages
            .filterNot { it.status == MessageStatus.STREAMING }
            .map(::messageDto),
    )

    @Serializable
    private data class AuthRequest(val username: String, val password: String)

    @Serializable
    private data class MeResponse(val user: CloudUserDto)

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY = ByteArray(0).toRequestBody(null)
    }
}

/** Conversation-sync DTO: keeps the local image id and stores the cloud id in remoteId. */
internal fun messageDto(message: ChatMessage): CloudMessageDto = CloudMessageDto(
    id = message.id,
    role = message.role.name.lowercase(),
    content = message.content,
    reasoning = message.reasoning,
    pdfName = message.pdfName,
    pdfText = message.pdfText,
    segments = message.segments.map(::segmentDto),
    model = message.model,
    images = message.images.map { image ->
        CloudImageRefDto(
            id = image.id,
            remoteId = image.remoteId,
            mimeType = image.mimeType,
            width = image.width,
            height = image.height,
        )
    },
    createdAt = message.createdAt,
    status = wireStatus(message.status),
    error = message.error,
)

/**
 * Relay jobs read image bytes from the server file store by [CloudImageRefDto.id],
 * so the cloud file id must be used as the id there (same as the web client).
 * Conversation sync keeps the local id and puts the cloud id in remoteId.
 */
internal fun relayMessageDto(message: ChatMessage): CloudMessageDto =
    messageDto(message).copy(
        images = message.images.map { image ->
            val remote = image.remoteId?.takeIf { it.isNotBlank() }
            CloudImageRefDto(
                id = remote ?: image.id,
                remoteId = remote,
                mimeType = image.mimeType,
                width = image.width,
                height = image.height,
            )
        },
    )

internal fun segmentDto(segment: MessageSegment): MessageSegmentDto = MessageSegmentDto(
    type = segment.type,
    text = segment.text,
    name = segment.name,
    label = segment.label,
    status = segment.status,
    detail = segment.detail,
)

/**
 * The server only accepts COMPLETE/STOPPED/ERROR for stored messages.
 * CANCELLED is the richer local/web status; it is stored as STOPPED.
 */
internal fun wireStatus(status: MessageStatus): String = when (status) {
    MessageStatus.CANCELLED -> "STOPPED"
    else -> status.name
}

fun ProviderDto.toLocalProvider(): ProviderConfig = ProviderConfig(
    id = id,
    name = name,
    protocol = runCatching { ApiProtocol.valueOf(protocol) }.getOrDefault(ApiProtocol.CHAT_COMPLETIONS),
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    models = models.map { it.toLocalModel() },
    systemPrompt = systemPrompt,
    temperature = temperature,
    maxTokens = maxTokens,
    localOnly = false,
    cloudSynced = true,
)

fun ProviderModelDto.toLocalModel(): ProviderModel = ProviderModel(
    id = id,
    name = name,
    contextWindow = contextWindow,
    maxOutputTokens = maxOutputTokens,
    reasoning = reasoning,
)

fun ProviderModel.toDto(): ProviderModelDto = ProviderModelDto(
    id = id,
    name = name,
    contextWindow = contextWindow,
    maxOutputTokens = maxOutputTokens,
    reasoning = reasoning,
)

fun ProviderConfig.toDto(): ProviderDto = ProviderDto(
    id = id,
    name = name,
    protocol = protocol.name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    models = models.map { it.toDto() },
    systemPrompt = systemPrompt,
    temperature = temperature,
    maxTokens = maxTokens,
)

fun CloudMessageDto.roleEnum(): MessageRole = runCatching { MessageRole.valueOf(role.uppercase()) }
    .getOrDefault(MessageRole.ASSISTANT)

fun CloudMessageDto.statusEnum(): MessageStatus = when (status.uppercase()) {
    "CANCELLED" -> MessageStatus.CANCELLED
    else -> runCatching { MessageStatus.valueOf(status.uppercase()) }
        .getOrDefault(MessageStatus.COMPLETE)
}

fun MessageSegmentDto.toLocalSegment(): MessageSegment = MessageSegment(
    type = type,
    text = text,
    name = name,
    label = label,
    status = status.ifBlank { MessageSegment.TOOL_STATUS_RUNNING },
    detail = detail,
)
