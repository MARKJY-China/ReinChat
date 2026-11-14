package com.reinchat.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

class SettingsStore(
    context: Context,
    private val legacyVault: SecureVault,
) {
    companion object {
        private const val TAG = "SettingsStore"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(context.filesDir, "settings.bin")
    private val localVault = LocalSettingsVault(context)
    private val lock = Mutex()

    fun load(): AppSettings {
        if (!file.exists()) return AppSettings()
        return try {
            val sealed = file.readBytes()
            val plain = when {
                localVault.isMine(sealed) -> localVault.open(sealed)
                SecureVault.isSealed(sealed) -> legacyVault.open(sealed)
                else -> error("Unknown settings envelope")
            }
            val settings = normalize(json.decodeFromString<AppSettings>(plain.decodeToString()))
            // Migrate legacy hardware-keystore settings to the portable local
            // envelope after a successful read. Failure leaves the legacy file
            // intact so nothing is lost.
            if (SecureVault.isSealed(sealed)) {
                runCatching { writeSettings(settings) }
            }
            settings
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to read settings; keeping a recovery copy", t)
            runCatching {
                file.copyTo(File(file.parentFile, "settings.bin.corrupt"), overwrite = true)
            }
            AppSettings()
        }
    }

    suspend fun save(settings: AppSettings): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching { writeSettings(settings) }
                .onFailure { Log.e(TAG, "Unable to save settings", it) }
                .isSuccess
        }
    }

    @Throws(Throwable::class)
    private fun writeSettings(settings: AppSettings) {
        val plain = json.encodeToString(settings).encodeToByteArray()
        val sealed = localVault.seal(plain)
        // Verify before replacing the only copy on disk.
        check(localVault.open(sealed).contentEquals(plain)) { "Settings round-trip check failed" }
        val temp = File(file.parentFile, "settings.bin.tmp")
        FileOutputStream(temp).use { output ->
            output.write(sealed)
            output.fd.sync()
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private fun normalize(settings: AppSettings): AppSettings {
        val providers = settings.providers.distinctBy { it.id }
        val active = settings.activeProviderId?.takeIf { id -> providers.any { it.id == id } }
            ?: providers.firstOrNull()?.id
        return settings.copy(providers = providers, activeProviderId = active)
    }
}
