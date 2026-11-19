package com.reinchat.app.data

import com.reinchat.app.net.CloudMessageDto
import com.reinchat.app.net.statusEnum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun defaultConversationTitlesAreRecognized() {
        assertTrue(isDefaultConversationTitle(""))
        assertTrue(isDefaultConversationTitle("   "))
        assertTrue(isDefaultConversationTitle("新对话"))
        assertTrue(isDefaultConversationTitle("新会话"))
        assertFalse(isDefaultConversationTitle("解释 Kotlin 协程"))
    }

    @Test
    fun cancelledAndStoppedAreBothTerminalCancellations() {
        assertTrue(MessageStatus.CANCELLED.isCancelled)
        assertTrue(MessageStatus.STOPPED.isCancelled)
        assertFalse(MessageStatus.COMPLETE.isCancelled)
        assertFalse(MessageStatus.STREAMING.isTerminal)
        assertTrue(MessageStatus.CANCELLED.isTerminal)
    }

    @Test
    fun providerModelsProvideOptionListAndLabels() {
        val provider = ProviderConfig(
            model = "",
            models = listOf(
                ProviderModel(id = "gpt-4o", name = "GPT-4o"),
                ProviderModel(id = "gpt-4o-mini", name = "GPT-4o mini"),
            ),
        )
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), provider.modelOptions)
        assertEquals("GPT-4o mini", provider.modelLabels["gpt-4o-mini"])
        assertEquals("gpt-4o", provider.activeModel)
    }

    @Test
    fun modelCommaListWinsOverStaleDirectory() {
        val provider = ProviderConfig(
            model = "a,b",
            models = listOf(ProviderModel(id = "a", name = "A")),
        )
        assertEquals(listOf("a", "b"), provider.modelOptions)
        assertEquals("A", provider.modelLabels["a"])
    }

    @Test
    fun cancelledWireStatusParsesBackAsCancelled() {
        val dto = CloudMessageDto(id = "m", role = "assistant", status = "CANCELLED")
        assertEquals(MessageStatus.CANCELLED, dto.statusEnum())
        val stopped = CloudMessageDto(id = "m2", role = "assistant", status = "STOPPED")
        assertEquals(MessageStatus.STOPPED, stopped.statusEnum())
    }
}
