package com.edu.ackline.security

import android.content.Context
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class PayloadKeyStore(context: Context) {

    private val appContext = context.applicationContext

    fun isReady(kid: String): Boolean = loadKeyStore().containsAlias(aliasFor(kid))

    fun getDecryptKey(kid: String): SecretKey? =
        (loadKeyStore().getKey(aliasFor(kid), null) as? SecretKey)

    /**
     * Imports the sole staged key exactly once. The staging file is always
     * removed after an import attempt so raw material is never retained for retries.
     */
    fun importStagedKeyIfPresent(kid: String): ImportResult {
        val stagingFile = File(appContext.filesDir, STAGING_FILE_NAME)
        if (!stagingFile.exists()) return ImportResult.NO_STAGING_FILE

        var rawKey: ByteArray? = null
        return try {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(aliasFor(kid))) {
                ImportResult.ALREADY_READY
            } else {
                val stagedKey = stagingFile.readBytes()
                rawKey = stagedKey
                if (stagedKey.size != KEY_BYTES) {
                    ImportResult.INVALID_KEY
                } else {
                    keyStore.setEntry(
                        aliasFor(kid),
                        KeyStore.SecretKeyEntry(SecretKeySpec(stagedKey, "AES")),
                        KeyProtection.Builder(KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build(),
                    )
                    ImportResult.IMPORTED
                }
            }
        } catch (_: Exception) {
            ImportResult.FAILED
        } finally {
            rawKey?.fill(0)
            removeStagingFile(stagingFile)
        }
    }

    internal fun deleteKeyForTest(kid: String) {
        loadKeyStore().deleteEntry(aliasFor(kid))
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun aliasFor(kid: String): String = "ackline.payload.$kid"

    private fun removeStagingFile(stagingFile: File) {
        if (!stagingFile.exists()) return
        runCatching {
            RandomAccessFile(stagingFile, "rw").use { file ->
                file.setLength(0)
                file.fd.sync()
            }
        }
        stagingFile.delete()
    }

    enum class ImportResult {
        NO_STAGING_FILE,
        IMPORTED,
        ALREADY_READY,
        INVALID_KEY,
        FAILED,
    }

    companion object {
        const val STAGING_FILE_NAME = ".e2ee_staging.bin"
        const val KEY_BYTES = 32
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}
