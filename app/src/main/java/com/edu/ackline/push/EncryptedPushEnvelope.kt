package com.edu.ackline.push

import java.util.Base64

internal data class EncryptedPushEnvelope(
    val version: String,
    val kid: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
) {
    companion object {
        const val VERSION = "1"
        const val NONCE_BYTES = 12
        const val GCM_TAG_BYTES = 16
        const val MAX_INNER_PAYLOAD_BYTES = 2_500
        private const val MAX_CIPHERTEXT_BYTES = MAX_INNER_PAYLOAD_BYTES + GCM_TAG_BYTES
        private val KID_PATTERN = Regex("[A-Za-z0-9._-]{1,64}")
        private val BASE64URL_PATTERN = Regex("[A-Za-z0-9_-]+")
        private val REQUIRED_KEYS = setOf("v", "kid", "nonce", "ciphertext")

        fun parse(data: Map<String, String>): ParseResult {
            if (data.keys != REQUIRED_KEYS) return ParseResult.Rejected(Failure.MALFORMED_ENVELOPE)

            val version = data["v"] ?: return ParseResult.Rejected(Failure.MALFORMED_ENVELOPE)
            if (version != VERSION) return ParseResult.Rejected(Failure.UNSUPPORTED_VERSION)

            val kid = data["kid"] ?: return ParseResult.Rejected(Failure.MALFORMED_ENVELOPE)
            if (!KID_PATTERN.matches(kid)) return ParseResult.Rejected(Failure.MALFORMED_ENVELOPE)

            val nonce = decodeBase64Url(data["nonce"])
                ?: return ParseResult.Rejected(Failure.MALFORMED_ENVELOPE)
            if (nonce.size != NONCE_BYTES) return ParseResult.Rejected(Failure.MALFORMED_ENVELOPE)

            val ciphertext = decodeBase64Url(data["ciphertext"])
                ?: return ParseResult.Rejected(Failure.MALFORMED_ENVELOPE)
            if (ciphertext.size < GCM_TAG_BYTES) {
                return ParseResult.Rejected(Failure.MALFORMED_ENVELOPE)
            }
            if (ciphertext.size > MAX_CIPHERTEXT_BYTES) {
                return ParseResult.Rejected(Failure.OVERSIZE)
            }

            return ParseResult.Success(
                EncryptedPushEnvelope(
                    version = version,
                    kid = kid,
                    nonce = nonce,
                    ciphertext = ciphertext,
                ),
            )
        }

        fun aad(version: String, kid: String): ByteArray =
            "ackline-e2ee|v=$version|kid=$kid".toByteArray(Charsets.UTF_8)

        private fun decodeBase64Url(value: String?): ByteArray? {
            if (value.isNullOrEmpty() || !BASE64URL_PATTERN.matches(value) || value.length % 4 == 1) {
                return null
            }
            return runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull()
        }
    }

    sealed interface ParseResult {
        data class Success(val envelope: EncryptedPushEnvelope) : ParseResult
        data class Rejected(val failure: Failure) : ParseResult
    }

    enum class Failure {
        MALFORMED_ENVELOPE,
        UNSUPPORTED_VERSION,
        OVERSIZE,
    }
}
