package com.edu.ackline.security

import com.edu.ackline.push.EncryptedPushEnvelope
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadCryptoVectorTest {

    @Test
    fun decryptsThePythonGeneratedDeterministicVector() {
        val plaintext = PayloadCrypto.decryptWithKey(vectorEnvelope(), SecretKeySpec(ByteArray(32) { it.toByte() }, "AES"))

        assertEquals(VECTOR_PLAINTEXT, plaintext.toString(Charsets.UTF_8))
    }

    @Test
    fun rejectsTamperingWrongNonceAndChangedAad() {
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val original = vectorEnvelope()
        val tamperedCiphertext = original.ciphertext.copyOf().also { bytes -> bytes[0] = (bytes[0].toInt() xor 1).toByte() }

        assertFailure(original.copy(ciphertext = tamperedCiphertext), key)
        assertFailure(original.copy(nonce = original.nonce.copyOf().also { it[0] = 1 }), key)
        assertFailure(original.copy(kid = "other-kid"), key)
        assertFailure(original, SecretKeySpec(ByteArray(32) { 7 }, "AES"))
    }

    private fun assertFailure(envelope: EncryptedPushEnvelope, key: SecretKeySpec) {
        assertTrue(runCatching { PayloadCrypto.decryptWithKey(envelope, key) }.isFailure)
    }

    private fun vectorEnvelope(): EncryptedPushEnvelope =
        EncryptedPushEnvelope(
            version = "1",
            kid = "test-vector",
            nonce = ByteArray(12) { it.toByte() },
            ciphertext = Base64.getUrlDecoder().decode(VECTOR_CIPHERTEXT),
        )

    private companion object {
        const val VECTOR_PLAINTEXT = "{\"protocol\":\"1\",\"notification_id\":\"vector-001\",\"level\":\"important\",\"title\":\"Vector\",\"message\":\"Non-sensitive test\",\"created_at\":\"2026-08-30T00:00:00Z\",\"ack_token\":\"vector-token\"}"
        const val VECTOR_CIPHERTEXT = "PCCmaaqRrXjiLbWxk9haQaG46ECZHTYfWROM6nM2adYjKoyKyqJm9waJT925pQQagjwW6Db0mfhW-lp2apeUgIQe6linuFINeXaQTLnqbZxX-OtOVA42G46c-alaypvDpJZ0lcEvu3PqdUjXaAFEDM5FTomAt0D7OZozkuOSTDSZYDD0gvr7ayXSePUN6nbyBs7TBW8zv2jtPIMojr2Ci_21pDefmq8KyBHwjrDr49W_s-dujbZdrCcmIPM89XMuGsY"
    }
}
