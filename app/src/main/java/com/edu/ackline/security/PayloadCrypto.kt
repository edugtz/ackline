package com.edu.ackline.security

import com.edu.ackline.push.EncryptedPushEnvelope
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class PayloadCrypto(
    private val payloadKeyStore: PayloadKeyStore,
) {
    fun decrypt(envelope: EncryptedPushEnvelope): DecryptResult {
        val key = try {
            payloadKeyStore.getDecryptKey(envelope.kid)
        } catch (_: Exception) {
            null
        } ?: return DecryptResult.Rejected(Failure.KEY_NOT_CONFIGURED)

        return try {
            DecryptResult.Success(decryptWithKey(envelope, key))
        } catch (_: Exception) {
            DecryptResult.Rejected(Failure.AUTHENTICATION_FAILED)
        }
    }

    sealed interface DecryptResult {
        data class Success(val plaintext: ByteArray) : DecryptResult
        data class Rejected(val failure: Failure) : DecryptResult
    }

    enum class Failure {
        KEY_NOT_CONFIGURED,
        AUTHENTICATION_FAILED,
    }

    companion object {
        internal fun decryptWithKey(
            envelope: EncryptedPushEnvelope,
            key: SecretKey,
        ): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, envelope.nonce),
            )
            cipher.updateAAD(EncryptedPushEnvelope.aad(envelope.version, envelope.kid))
            return cipher.doFinal(envelope.ciphertext)
        }
    }
}
