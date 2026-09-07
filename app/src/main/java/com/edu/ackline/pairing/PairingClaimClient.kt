package com.edu.ackline.pairing

import com.edu.ackline.network.HttpsConnectionFactory
import com.edu.ackline.network.normalizeHttpsBaseUrl
import com.edu.ackline.push.EncryptedPushEnvelope
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

data class PairingClaimRequest(
    val sessionId: String,
    val token: String,
    val fid: String,
) {
    override fun toString(): String = "PairingClaimRequest(<redacted>)"
}

data class PairingClaimResponse(
    val kid: String,
    val e2eeKeyB64: String,
    val ackBaseUrl: String,
) {
    override fun toString(): String = "PairingClaimResponse(<redacted>)"
}

sealed interface PairingClaimResult {
    data class Success(val response: PairingClaimResponse) : PairingClaimResult

    data class Failure(val reason: PairingClaimFailure) : PairingClaimResult
}

enum class PairingClaimFailure {
    INVALID_ENDPOINT,
    INVALID_REQUEST,
    TRANSPORT,
    CLIENT_ERROR,
    MALFORMED_RESPONSE,
    RESPONSE_TOO_LARGE,
    UNEXPECTED_STATUS,
    INVALID,
    EXPIRED,
    CONSUMED,
    TAILSCALE_IDENTITY_REQUIRED,
    REPLACE_REQUIRED,
    RATE_LIMITED,
    SERVER_MISCONFIGURED,
}

/** HTTPS client for the one-time H1 pairing claim endpoint. */
class PairingClaimClient(
    private val connectionFactory: HttpsConnectionFactory,
) {

    fun claim(
        pairingEndpoint: String,
        sessionId: String,
        token: String,
        fid: String,
    ): PairingClaimResult = claim(
        pairingEndpoint = pairingEndpoint,
        request = PairingClaimRequest(
            sessionId = sessionId,
            token = token,
            fid = fid,
        ),
    )

    fun claim(
        pairingEndpoint: String,
        request: PairingClaimRequest,
    ): PairingClaimResult {
        val endpoint = normalizePairingEndpoint(pairingEndpoint)
            ?: return PairingClaimResult.Failure(PairingClaimFailure.INVALID_ENDPOINT)
        if (!isValidClaimValue(request.sessionId, MAX_SESSION_ID_LENGTH) ||
            !isValidClaimValue(request.token, MAX_TOKEN_LENGTH) ||
            !isValidClaimValue(request.fid, MAX_FID_LENGTH)
        ) {
            return PairingClaimResult.Failure(PairingClaimFailure.INVALID_REQUEST)
        }

        val url = runCatching { URI(endpoint).toURL() }.getOrNull()
            ?: return PairingClaimResult.Failure(PairingClaimFailure.INVALID_ENDPOINT)
        val connection = try {
            connectionFactory.open(url)
        } catch (_: IOException) {
            return PairingClaimResult.Failure(PairingClaimFailure.TRANSPORT)
        } catch (_: IllegalArgumentException) {
            return PairingClaimResult.Failure(PairingClaimFailure.CLIENT_ERROR)
        } catch (_: SecurityException) {
            return PairingClaimResult.Failure(PairingClaimFailure.CLIENT_ERROR)
        }

        return try {
            val requestBody = JSONObject()
                .put("session_id", request.sessionId)
                .put("token", request.token)
                .put("fid", request.fid)
                .toString()
                .toByteArray(Charsets.UTF_8)

            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.requestMethod = "POST"
                connection.doInput = true
                connection.doOutput = true
                connection.useCaches = false
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.setFixedLengthStreamingMode(requestBody.size)
                connection.outputStream.use { output -> output.write(requestBody) }

                val statusCode = connection.responseCode
                when (statusCode) {
                    HTTP_OK -> parseSuccessResponse(connection)
                    HTTP_BAD_REQUEST,
                    HTTP_FORBIDDEN,
                    HTTP_CONFLICT,
                    HTTP_TOO_MANY_REQUESTS,
                    HTTP_SERVER_ERROR,
                    -> parseErrorResponse(statusCode, connection)

                    else -> PairingClaimResult.Failure(PairingClaimFailure.UNEXPECTED_STATUS)
                }
            } finally {
                requestBody.fill(0)
            }
        } catch (_: IOException) {
            PairingClaimResult.Failure(PairingClaimFailure.TRANSPORT)
        } catch (_: IllegalArgumentException) {
            PairingClaimResult.Failure(PairingClaimFailure.CLIENT_ERROR)
        } catch (_: SecurityException) {
            PairingClaimResult.Failure(PairingClaimFailure.CLIENT_ERROR)
        } catch (_: Exception) {
            PairingClaimResult.Failure(PairingClaimFailure.CLIENT_ERROR)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun parseSuccessResponse(connection: HttpsURLConnection): PairingClaimResult {
        return when (val body = readResponseBody(connection.inputStream)) {
            BodyReadResult.InvalidUtf8 -> malformedResponse()
            BodyReadResult.TooLarge -> PairingClaimResult.Failure(
                PairingClaimFailure.RESPONSE_TOO_LARGE,
            )

            is BodyReadResult.Body -> {
                val response = runCatching { JSONObject(body.value) }.getOrNull()
                    ?: return malformedResponse()
                if (response.opt("ok") != true) return malformedResponse()

                val kid = response.requiredString("kid", MAX_KID_LENGTH)
                    ?.takeIf { EncryptedPushEnvelope.isValidKid(it) }
                    ?: return malformedResponse()
                val e2eeKeyB64 = response.requiredString(
                    "e2ee_key_b64",
                    MAX_E2EE_KEY_B64_LENGTH,
                )?.takeIf(::isValidE2eeKeyEncoding)
                    ?: return malformedResponse()
                val ackBaseUrl = response.requiredString(
                    "ack_base_url",
                    MAX_ACK_BASE_URL_LENGTH,
                )?.let(::normalizeHttpsBaseUrl)
                    ?: return malformedResponse()

                PairingClaimResult.Success(
                    PairingClaimResponse(
                        kid = kid,
                        e2eeKeyB64 = e2eeKeyB64,
                        ackBaseUrl = ackBaseUrl,
                    ),
                )
            }
        }
    }

    private fun parseErrorResponse(
        statusCode: Int,
        connection: HttpsURLConnection,
    ): PairingClaimResult {
        val input = connection.errorStream
            ?: runCatching { connection.inputStream }.getOrNull()
            ?: return malformedResponse()

        return when (val body = readResponseBody(input)) {
            BodyReadResult.InvalidUtf8 -> malformedResponse()
            BodyReadResult.TooLarge -> PairingClaimResult.Failure(
                PairingClaimFailure.RESPONSE_TOO_LARGE,
            )

            is BodyReadResult.Body -> {
                val errorCode = runCatching { JSONObject(body.value) }
                    .getOrNull()
                    ?.takeIf { it.opt("ok") == false }
                    ?.requiredString("error", MAX_ERROR_LENGTH)

                val failure = when (statusCode) {
                    HTTP_BAD_REQUEST ->
                        if (errorCode == "invalid_request") {
                            PairingClaimFailure.INVALID_REQUEST
                        } else {
                            null
                        }

                    HTTP_FORBIDDEN -> when (errorCode) {
                        "invalid" -> PairingClaimFailure.INVALID
                        "expired" -> PairingClaimFailure.EXPIRED
                        "consumed",
                        "replay",
                        "replayed",
                        -> PairingClaimFailure.CONSUMED

                        "tailscale_identity_required" ->
                            PairingClaimFailure.TAILSCALE_IDENTITY_REQUIRED

                        else -> null
                    }

                    HTTP_CONFLICT ->
                        if (errorCode == "replace_required") {
                            PairingClaimFailure.REPLACE_REQUIRED
                        } else {
                            null
                        }

                    HTTP_TOO_MANY_REQUESTS ->
                        if (errorCode == "rate_limited") {
                            PairingClaimFailure.RATE_LIMITED
                        } else {
                            null
                        }

                    HTTP_SERVER_ERROR ->
                        if (errorCode == "server_misconfigured") {
                            PairingClaimFailure.SERVER_MISCONFIGURED
                        } else {
                            null
                        }

                    else -> null
                }

                failure?.let { PairingClaimResult.Failure(it) } ?: malformedResponse()
            }
        }
    }

    private fun readResponseBody(input: InputStream): BodyReadResult {
        input.use { stream ->
            val output = ByteArrayOutputStream(MAX_RESPONSE_BYTES)
            val buffer = ByteArray(READ_BUFFER_BYTES)
            var totalBytes = 0

            while (true) {
                val remainingBytes = MAX_RESPONSE_BYTES - totalBytes
                val readLimit = minOf(buffer.size, remainingBytes.coerceAtLeast(1))
                val bytesRead = stream.read(buffer, 0, readLimit)
                if (bytesRead == -1) break
                if (bytesRead == 0) continue
                if (totalBytes == MAX_RESPONSE_BYTES) return BodyReadResult.TooLarge

                output.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }

            val text = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(output.toByteArray()))
                    .toString()
            } catch (_: CharacterCodingException) {
                return BodyReadResult.InvalidUtf8
            }
            return BodyReadResult.Body(text)
        }
    }

    private sealed interface BodyReadResult {
        data class Body(val value: String) : BodyReadResult

        data object TooLarge : BodyReadResult

        data object InvalidUtf8 : BodyReadResult
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 10_000
        const val MAX_RESPONSE_BYTES = 64 * 1024
        const val READ_BUFFER_BYTES = 8 * 1024
        const val MAX_SESSION_ID_LENGTH = 256
        const val MAX_TOKEN_LENGTH = 512
        const val MAX_FID_LENGTH = 256
        const val MAX_KID_LENGTH = 64
        const val MAX_E2EE_KEY_B64_LENGTH = 512
        const val MAX_ACK_BASE_URL_LENGTH = 2_048
        const val MAX_ERROR_LENGTH = 128
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_CONFLICT = 409
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
    }
}

internal fun normalizePairingEndpoint(value: String): String? {
    val normalized = value.trim()
    if (normalized.isBlank() || normalized.length > 2_048) return null

    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        uri.host.isNullOrBlank() ||
        uri.rawQuery != null ||
        uri.rawFragment != null ||
        uri.userInfo != null
    ) {
        return null
    }

    return runCatching {
        uri.toURL()
        normalized
    }.getOrNull()
}

internal fun isValidClaimValue(value: String, maxLength: Int): Boolean =
    value.isNotBlank() &&
        value.length <= maxLength &&
        value.none(Char::isISOControl)

internal fun isValidE2eeKeyEncoding(value: String): Boolean {
    if (value.isBlank() || value.length > 512) return false
    val decoded = runCatching { Base64.getDecoder().decode(value) }.getOrNull() ?: return false
    decoded.fill(0)
    return true
}

internal fun decodeE2eeKey(value: String): ByteArray? =
    if (value.isBlank() || value.length > 512) {
        null
    } else {
        runCatching { Base64.getDecoder().decode(value) }.getOrNull()
    }

private fun JSONObject.requiredString(name: String, maxLength: Int): String? {
    val value = opt(name) as? String ?: return null
    return value.takeIf { isValidClaimValue(it, maxLength) }
}

private fun malformedResponse(): PairingClaimResult =
    PairingClaimResult.Failure(PairingClaimFailure.MALFORMED_RESPONSE)
