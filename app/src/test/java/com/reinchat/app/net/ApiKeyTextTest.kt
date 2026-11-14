package com.reinchat.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ApiKeyTextTest {
    @Test
    fun stripsInvisibleCharactersAndNormalizesDashes() {
        val raw = "sk-\u200Bdemo\u00A0key\u2011part"
        assertEquals("sk-demokey-part", normalizeApiKey(raw))
    }

    @Test
    fun acceptsVisibleAscii() {
        assertNull(apiKeyHeaderProblem("VENICE_INFERENCE_KEY_44i8FgQRA7REtGYL-"))
    }

    @Test
    fun stripsNonAsciiLookalikesAndChineseCharacters() {
        assertEquals("sk-demokey", normalizeApiKey("sk-é中demo时key"))
    }

    @Test
    fun reportsRawNonAsciiOnlyWhenCalledDirectly() {
        val problem = apiKeyHeaderProblem("sk-é")
        assertNotNull(problem)
        assertEquals(true, problem!!.contains("U+00E9"))
    }
}
