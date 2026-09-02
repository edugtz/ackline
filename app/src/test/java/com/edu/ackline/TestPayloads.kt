package com.edu.ackline

import com.edu.ackline.push.EncryptedPushEnvelope
import com.edu.ackline.security.PayloadCrypto
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object TestPayloads {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    fun encryptedData(
        notificationId: String = "test-001",
        kid: String = BuildConfig.PAYLOAD_ENCRYPTION_KID,
        plaintext: ByteArray = payload(notificationId),
    ): Map<String, String> {
        val nonce = ByteArray(EncryptedPushEnvelope.NONCE_BYTES) { it.toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            GCMParameterSpec(128, nonce),
        )
        cipher.updateAAD(EncryptedPushEnvelope.aad(EncryptedPushEnvelope.VERSION, kid))
        val ciphertext = cipher.doFinal(plaintext)

        return mapOf(
            "v" to EncryptedPushEnvelope.VERSION,
            "kid" to kid,
            "nonce" to Base64.getUrlEncoder().withoutPadding().encodeToString(nonce),
            "ciphertext" to Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext),
        )
    }

    fun payload(
        notificationId: String = "test-001",
        level: String = "important",
        title: String = "Ackline test",
        message: String = "Non-sensitive test",
    ): ByteArray =
        """{"protocol":"1","notification_id":"$notificationId","level":"$level","title":"$title","message":"$message","created_at":"2026-08-30T00:00:00Z","ack_token":"test-token"}"""
            .toByteArray(Charsets.UTF_8)

    fun decrypt(envelope: EncryptedPushEnvelope): PayloadCrypto.DecryptResult =
        try {
            PayloadCrypto.DecryptResult.Success(PayloadCrypto.decryptWithKey(envelope, key))
        } catch (_: Exception) {
            PayloadCrypto.DecryptResult.Rejected(
                PayloadCrypto.Failure.AUTHENTICATION_FAILED,
            )
        }
}
