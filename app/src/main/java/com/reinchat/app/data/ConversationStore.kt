package com.reinchat.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class ConversationArchive(
    val version: Int = 1,
    val conversations: List<Conversation> = emptyList(),
)

class ConversationStore(context: Context) {
    companion object {
        private const val TAG = "ConversationStore"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(context.filesDir, "conversations.json")
    private val lock = Mutex()

    fun load(): List<Conversation> {
        if (!file.exists()) return emptyList()
        return try {
            val archive = json.decodeFromString<ConversationArchive>(file.readText())
            archive.conversations
                .map(::normalize)
                .filter { it.messages.isNotEmpty() }
                .sortedByDescending { it.updatedAt }
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to read conversations; keeping a recovery copy", t)
            runCatching {
                file.copyTo(File(file.parentFile, "conversations.json.corrupt"), overwrite = true)
            }
            emptyList()
        }
    }

    suspend fun save(conversations: List<Conversation>): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                val text = json.encodeToString(ConversationArchive(conversations = conversations))
                val temp = File(file.parentFile, "conversations.json.tmp")
                temp.writeText(text)
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
                true
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to save conversations", t)
                false
            }
        }
    }

    private fun normalize(conversation: Conversation): Conversation {
        val cleaned = conversation.messages.mapNotNull { message ->
            when {
                message.status == MessageStatus.STREAMING -> {
                    if (message.content.isBlank() && message.reasoning.isBlank()) null else message.copy(
                        status = MessageStatus.CANCELLED,
                        error = null,
                    )
                }
                message.status.isCancelled &&
                    message.content.isBlank() &&
                    message.reasoning.isBlank() &&
                    message.segments.isEmpty() -> null
                else -> message
            }
        }
        val title = conversation.title.ifBlank {
            val first = cleaned.firstOrNull { it.role == MessageRole.USER }
            titleFromPrompt(first?.content.orEmpty(), first?.images?.size ?: 0)
        }
        return conversation.copy(title = title, messages = cleaned)
    }
}
