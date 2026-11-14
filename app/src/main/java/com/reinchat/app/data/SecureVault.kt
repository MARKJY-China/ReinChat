package com.reinchat.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Small AES/GCM envelope for local settings. The master key lives in the
 * AndroidKeyStore where available, with a private app-data fallback so the
 * client still works on vendors with an incomplete Keystore implementation.
 */
class SecureVault(private val context: Context) {

    companion object {
        private const val TAG = "SecureVault"
        private const val KEY_ALIAS = "reinchat.settings.v1"
        private const val FALLBACK_PREFS = "reinchat.secure"
        private const val FALLBACK_KEY = "fallback_key"
        private val MAGIC = byteArrayOf(0x52, 0x43, 0x47, 0x01) // RCG1
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128

        fun isSealed(data: ByteArray): Boolean {
            if (data.size <= MAGIC.size + IV_LENGTH) return false
            return MAGIC.indices.all { data[it] == MAGIC[it] }
        }
    }

    private val fallbackPrefs by lazy {
        context.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                    .apply {
                        init(
                            KeyGenParameterSpec.Builder(
                                KEY_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setKeySize(256)
                                .build()
                        )
                    }
                    .generateKey()
        } catch (t: Throwable) {
            Log.w(TAG, "AndroidKeyStore unavailable, using local fallback", t)
            fallbackKey()
        }
    }

    private fun fallbackKey(): SecretKey {
        val encoded = fallbackPrefs.getString(FALLBACK_KEY, null)
        if (encoded != null) {
            return SecretKeySpec(Base64.decode(encoded, Base64.NO_WRAP), "AES")
        }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        fallbackPrefs.edit()
            .putString(FALLBACK_KEY, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .commit()
        return SecretKeySpec(bytes, "AES")
    }

    fun seal(plain: ByteArray): ByteArray {
        // AndroidKeyStore GCM keys require the cipher to generate the IV itself.
        // Caller-provided IVs are only permitted during decryption.
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val iv = cipher.iv
        require(iv.size == IV_LENGTH) { "Unexpected GCM IV length: ${iv.size}" }
        val cipherText = cipher.doFinal(plain)
        return ByteArray(MAGIC.size + IV_LENGTH + cipherText.size).also { out ->
            MAGIC.copyInto(out, 0)
            iv.copyInto(out, MAGIC.size)
            cipherText.copyInto(out, MAGIC.size + IV_LENGTH)
        }
    }

    fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > MAGIC.size + IV_LENGTH) { "Invalid sealed data" }
        for (i in MAGIC.indices) {
            require(sealed[i] == MAGIC[i]) { "Invalid sealed header" }
        }
        val iv = sealed.copyOfRange(MAGIC.size, MAGIC.size + IV_LENGTH)
        val cipherText = sealed.copyOfRange(MAGIC.size + IV_LENGTH, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(cipherText)
    }
}
