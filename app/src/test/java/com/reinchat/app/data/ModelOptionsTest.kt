package com.reinchat.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelOptionsTest {
    @Test
    fun parsesCommaSeparatedModels() {
        assertEquals(
            listOf("gpt-4o-mini", "deepseek-chat", "gpt-4.1"),
            modelOptionsOf(" gpt-4o-mini, deepseek-chat ,, gpt-4.1, gpt-4o-mini "),
        )
    }

    @Test
    fun activeModelFallsBackToFirstValidOption() {
        val provider = ProviderConfig(
            model = "alpha, beta",
            selectedModel = "removed-model",
        )
        assertEquals("alpha", provider.activeModel)
        assertTrue(provider.modelOptions.contains("beta"))
    }
}
