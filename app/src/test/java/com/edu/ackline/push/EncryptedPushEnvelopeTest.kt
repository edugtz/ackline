package com.edu.ackline.push

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedPushEnvelopeTest {

    @Test
    fun acceptsOnlyTheExactValidV1Envelope() {
        val result = EncryptedPushEnvelope.parse(validData())

        assertTrue(result is EncryptedPushEnvelope.ParseResult.Success)
        val envelope = (result as EncryptedPushEnvelope.ParseResult.Success).envelope
        assertEquals("1", envelope.version)
        assertEquals("test-vector", envelope.kid)
        assertEquals(12, envelope.nonce.size)
        assertEquals(16, envelope.ciphertext.size)
    }

    @Test
    fun rejectsMissingExtraAndUnsupportedEnvelopeFields() {
        val missingKeys = listOf("v", "kid", "nonce", "ciphertext")
        missingKeys.forEach { key ->
            assertFailure(validData() - key)
        }
        assertFailure(validData() + ("title" to "private"))
        assertFailure(validData() + ("v" to "2"))
    }

    @Test
    fun rejectsInvalidKidBase64NonceAndCiphertextBounds() {
        assertFailure(validData() + ("kid" to "not valid"))
        assertFailure(validData() + ("nonce" to "not=base64"))
        assertFailure(validData() + ("nonce" to base64Url(ByteArray(11))))
        assertFailure(validData() + ("ciphertext" to base64Url(ByteArray(15))))
        assertFailure(validData() + ("ciphertext" to base64Url(ByteArray(2_517))))
    }

    @Test
    fun constructsTheExactCanonicalAad() {
        assertEquals(
            "ackline-e2ee|v=1|kid=test-vector",
            EncryptedPushEnvelope.aad("1", "test-vector").toString(Charsets.UTF_8),
        )
    }

    private fun assertFailure(data: Map<String, String>) {
        assertTrue(EncryptedPushEnvelope.parse(data) is EncryptedPushEnvelope.ParseResult.Rejected)
    }

    private fun validData(): Map<String, String> =
        mapOf(
            "v" to "1",
            "kid" to "test-vector",
            "nonce" to base64Url(ByteArray(12)),
            "ciphertext" to base64Url(ByteArray(16)),
        )

    private fun base64Url(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)
}
