package com.reinchat.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val manifest = """
        [LatestVersion]
        0.4.1
        [MandatoryUpdate]
        Yes
        [DownloadURL]
        https://pan.szcmate.cn/f/d1LSG/ReinChat-0.4.1.apk
        [UpdateNotes]
        修复了一些已知问题。
        优化下载体验。
    """.trimIndent()

    @Test
    fun parsesMandatoryUpdate() {
        val update = UpdateChecker.parse(manifest, "0.4.0")
        assertEquals("0.4.1", update?.latestVersion)
        assertEquals(true, update?.mandatory)
        assertEquals("https://pan.szcmate.cn/f/d1LSG/ReinChat-0.4.1.apk", update?.downloadUrl)
        assertEquals("修复了一些已知问题。\n优化下载体验。", update?.notes)
    }

    @Test
    fun ignoresSameOrOlderVersion() {
        assertNull(UpdateChecker.parse(manifest, "0.4.1"))
        assertNull(UpdateChecker.parse(manifest, "0.5.0"))
    }

    @Test
    fun comparesNumericSegments() {
        assertTrue(UpdateChecker.isNewer("0.4.10", "0.4.9"))
        assertTrue(UpdateChecker.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("0.4.1", "0.4.1"))
    }
}
