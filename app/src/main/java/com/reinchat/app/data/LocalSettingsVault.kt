package com.reinchat.app.data

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A portable at-rest cipher for settings. Unlike [SecureVault], its key lives in
 * app-private preferences and therefore survives on Android compatibility
 * environments (for example vendor Android runtimes) that do not implement a
 * stable AndroidKeyStore across process restarts.
 *
 * This still keeps settings and API keys out of plaintext, inside the app
 * sandbox. Where the OS key store is trustworthy, [SecureVault] remains
 * available for reading legacy data.
 */
class LocalSettingsVault(context: Context) {

    companion object {
        private const val TAG = "LocalSettingsVault"
        private const val PREFS = "reinchat.local_vault"
        private const val KEY = "aes_key_v1"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
        private val MAGIC = byteArrayOf(0x52, 0x43, 0x53, 0x31) // RCS1
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKeySpec {
        val existing = prefs.getString(KEY, null)
        if (existing != null) {
            val raw = runCatching { Base64.decode(existing, Base64.NO_WRAP) }
                .getOrElse { ByteArray(0) }
            if (raw.size == 32) return SecretKeySpec(raw, "AES")
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val committed = prefs.edit()
            .putString(KEY, Base64.encodeToString(raw, Base64.NO_WRAP))
            .commit()
        if (!committed) {
            // Do not encrypt data with a key that will disappear after restart.
            Log.e(TAG, "Unable to persist the local settings key")
            throw IllegalStateException("本机设置密钥写入失败")
        }
        return SecretKeySpec(raw, "AES")
    }

    fun seal(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }
        val encrypted = cipher.doFinal(plain)
        return ByteArray(MAGIC.size + IV_LENGTH + encrypted.size).also { out ->
            MAGIC.copyInto(out, 0)
            iv.copyInto(out, MAGIC.size)
            encrypted.copyInto(out, MAGIC.size + IV_LENGTH)
        }
    }

    fun isMine(data: ByteArray): Boolean {
        if (data.size <= MAGIC.size + IV_LENGTH) return false
        return MAGIC.indices.all { data[it] == MAGIC[it] }
    }

    fun open(sealed: ByteArray): ByteArray {
        require(isMine(sealed)) { "Invalid local settings envelope" }
        val iv = sealed.copyOfRange(MAGIC.size, MAGIC.size + IV_LENGTH)
        val encrypted = sealed.copyOfRange(MAGIC.size + IV_LENGTH, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(encrypted)
    }
}
