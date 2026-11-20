package com.reinchat.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.reinchat.app.data.ApiProtocol
import com.reinchat.app.data.AppSettings
import com.reinchat.app.data.ChatImage
import com.reinchat.app.data.ChatMessage
import com.reinchat.app.data.CloudAccount
import com.reinchat.app.data.Conversation
import com.reinchat.app.data.ConversationStore
import com.reinchat.app.data.MessageRole
import com.reinchat.app.data.MessageSegment
import com.reinchat.app.data.ProviderConfig
import com.reinchat.app.data.SecureVault
import com.reinchat.app.data.SettingsStore
import com.reinchat.app.net.CloudEndpoint
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug-only helper used by the screenshot/verification flow. It is not part of
 * the release APK.
 */
class DebugSeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsStore = SettingsStore(application, SecureVault(application))
        if (intent.getBooleanExtra("server_only", false)) {
            runBlocking {
                settingsStore.save(AppSettings(cloudServerUrl = "http://127.0.0.1:8080"))
            }
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }
        if (intent.getBooleanExtra("demo_cloud", false)) {
            seedDemoCloud(settingsStore)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }
        val existing = settingsStore.load()
        val settings = AppSettings(
            providers = listOf(
                ProviderConfig(
                    id = "local-chat",
                    name = "Local Lab",
                    protocol = ApiProtocol.CHAT_COMPLETIONS,
                    baseUrl = "http://127.0.0.1:8787/v1",
                    model = "rein-demo",
                    apiKey = "sk-demo",
                    systemPrompt = "你是 ReinChat 的演示助手，回答简洁、清晰。",
                ),
                ProviderConfig(
                    id = "local-responses",
                    name = "Local Responses",
                    protocol = ApiProtocol.RESPONSES,
                    baseUrl = "http://127.0.0.1:8787/v1",
                    model = "rein-demo-responses",
                    apiKey = "sk-demo",
                    systemPrompt = "",
                ),
            ),
            activeProviderId = "local-chat",
            cloudAccount = existing.cloudAccount,
            cloudServerUrl = existing.cloudServerUrl.ifBlank { "http://127.0.0.1:8080" },
        )

        runBlocking {
            settingsStore.save(settings)
            val store = ConversationStore(application)
            when {
                intent.getBooleanExtra("seed_demo", false) -> store.save(buildDemoConversations())
                intent.getBooleanExtra("clear_conversations", false) -> store.save(emptyList())
            }
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun buildDemoConversations(): List<Conversation> {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)
            .atTime(9, 32)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val imageFile = File(filesDir, "images").apply { mkdirs() }
            .resolve("seed-liquid-demo.jpg")
        resources.openRawResource(R.raw.liquid_demo).use { input ->
            imageFile.outputStream().use { output -> input.copyTo(output) }
        }
        val demoImage = ChatImage(
            id = "seed-image-1",
            path = imageFile.absolutePath,
            mimeType = "image/jpeg",
            width = 1200,
            height = 800,
        )

        val first = Conversation(
            id = "seed-conversation-principles",
            title = "液态玻璃的三个层次",
            createdAt = now,
            updatedAt = now + 1_000,
            messages = listOf(
                ChatMessage(
                    id = "seed-principles-user",
                    role = MessageRole.USER,
                    content = "用三个层次解释液态玻璃的设计原则",
                    pdfName = "设计系统评审 2026.pdf",
                    pdfText = "透光：保留底色。折射：用高光与内阴影暗示表面弧度。留白：让内容浮起来。",
                    createdAt = now,
                ),
                ChatMessage(
                    id = "seed-principles-assistant",
                    role = MessageRole.ASSISTANT,
                    content = """
                        ## 液态玻璃的三重层次

                        液态玻璃不是一种材质，而是一种**界面语言**。

                        - **透**：让底色仍然可感知，而不是盖上一层灰
                        - **折**：用高光、内阴影和细边框暗示表面的弧度
                        - **动**：让出现与离开像液体一样连续

                        > 好的界面不会抢走注意力，而是让内容浮起来。
                    """.trimIndent(),
                    segments = listOf(
                        MessageSegment.reasoning(
                            "用户要的是设计原则，而不是具体实现。先看附件 PDF 里反复出现的三个词：透光、折射、留白。"
                        ),
                        MessageSegment.tool(
                            name = "web_search",
                            status = MessageSegment.TOOL_STATUS_DONE,
                            detail = "液态玻璃设计原则\n1. 透：保留底层色彩\n2. 折：高光与内阴影\n3. 动：连续的空间过渡",
                            label = "搜索网页",
                        ),
                        MessageSegment.text("\u5148\u7ed9\u4e2a\u521d\u6b65\u7ed3\u8bba\uff1a\u900f\u3001\u6298\u3001\u52a8\u4e09\u5c42\u3002\u8fd8\u5dee\u4e00\u4e2a\u6848\u4f8b\uff0c\u7ee7\u7eed\u67e5\u3002"),
                        MessageSegment.reasoning("搜索结果确认了这三层，再找一个具体案例验证。"),
                        MessageSegment.tool(
                            name = "fetch_url",
                            status = MessageSegment.TOOL_STATUS_FAILED,
                            detail = "读取 https://example.com/glass 失败：连接超时（10s）",
                            label = "浏览网页",
                        ),
                        MessageSegment.reasoning(
                            "网页打不开，回到 PDF 和搜索结果，按「透、折、动」三个层次组织回答。"
                        ),
                        MessageSegment.text("\u4ee5\u4e0a\u662f\u6700\u7ec8\u56de\u7b54\uff1a\u6db2\u6001\u73bb\u7483\u7531\u900f\u3001\u6298\u3001\u52a8\u4e09\u5c42\u6784\u6210\u3002"),
                    ),                    createdAt = now + 1_000,
                ),
            ),
        )

        val second = Conversation(
            id = "seed-conversation-image",
            title = "图片里的液态玻璃",
            createdAt = now - 8 * 60_000,
            updatedAt = now - 8 * 60_000,
            messages = listOf(
                ChatMessage(
                    id = "seed-image-user",
                    role = MessageRole.USER,
                    content = "这张图让你想到什么？",
                    images = listOf(demoImage),
                    createdAt = now - 8 * 60_000,
                ),
                ChatMessage(
                    id = "seed-image-assistant",
                    role = MessageRole.ASSISTANT,
                    content = """
                        ## 像一层会呼吸的膜

                        我看到三颗悬浮的玻璃泡，被同一片薄荷色光晕包裹。它让我想到 ReinChat 想做的事：**让内容浮起来，而不是让界面压住内容。**

                        - 透光：背景仍然在场
                        - 折射：边缘有细微的高光
                        - 留白：让每一次出现都有呼吸感

                        如果把它变成界面，我会保留大面积的低饱和渐变，只在交互发生时点亮一小块颜色。
                    """.trimIndent(),
                    createdAt = now - 8 * 60_000 + 1_000,
                ),
            ),
        )

        val third = Conversation(
            id = "seed-conversation-hello",
            title = "让思绪，发生",
            createdAt = now - 27 * 60_000,
            updatedAt = now - 27 * 60_000,
            messages = listOf(
                ChatMessage(
                    id = "seed-hello-user",
                    role = MessageRole.USER,
                    content = "你好，ReinChat",
                    createdAt = now - 27 * 60_000,
                ),
                ChatMessage(
                    id = "seed-hello-assistant",
                    role = MessageRole.ASSISTANT,
                    content = "你好，我是 **ReinChat（驭聊）**。\n\n把复杂留给我，把思绪交给你。今天想聊点什么？",
                    createdAt = now - 27 * 60_000 + 900,
                ),
            ),
        )

        return listOf(first, second, third)
    }

    /**
     * Signs the debug build into a local mock server so the OCR probe can drive
     * the real relay/OCR code path without a production account.
     */
    private fun seedDemoCloud(settingsStore: SettingsStore) {
        val raw = intent.getStringExtra("cloud_server")?.trim()
            .takeUnless { it.isNullOrBlank() } ?: "http://10.0.2.2:8787"
        val normalized = runCatching { CloudEndpoint.normalize(raw) }
            .getOrElse { "http://10.0.2.2:8787/api/v1" }
        val origin = normalized.removeSuffix("/api/v1")
        val account = CloudAccount(
            username = "probe",
            serverUrl = normalized,
            token = "probe-token",
            expiresAt = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000,
            level = 2,
        )
        val provider = ProviderConfig(
            id = "probe-provider",
            name = "Probe Provider",
            protocol = ApiProtocol.CHAT_COMPLETIONS,
            baseUrl = "$origin/v1",
            apiKey = "sk-probe",
            model = "probe-model",
            selectedModel = "probe-model",
            localOnly = true,
        )
        runBlocking {
            settingsStore.save(
                AppSettings(
                    providers = listOf(provider),
                    activeProviderId = provider.id,
                    cloudAccount = if (intent.getBooleanExtra("no_cloud", false)) null else account,
                    cloudServerUrl = normalized,
                )
            )
            ConversationStore(application).save(emptyList())
        }
    }
}
