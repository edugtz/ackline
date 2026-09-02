package com.edu.ackline.recovery

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection

class HttpsRecoveryRemoteClient(
    private val ackBaseUrl: String,
    private val connectionFactory: (URL) -> HttpsURLConnection? = ::openHttpsConnection,
) : RecoveryRemoteClient {

    override fun fetchPending(): RecoveryRemoteResult {
        if (ackBaseUrl.isBlank()) {
            return RecoveryRemoteResult.NotConfigured
        }

        val url = buildRecoveryUrl(ackBaseUrl)
            ?: return RecoveryRemoteResult.PermanentFailure(
                RecoveryErrorCategory.CONFIGURATION_ERROR,
            )

        val connection = try {
            connectionFactory(url)
        } catch (_: IOException) {
            return RecoveryRemoteResult.TransientFailure
        } catch (_: IllegalArgumentException) {
            return RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CLIENT_ERROR)
        } catch (_: SecurityException) {
            return RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CLIENT_ERROR)
        } ?: return RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CLIENT_ERROR)

        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            connection.doInput = true
            connection.doOutput = false
            connection.useCaches = false

            when (val statusCode = connection.responseCode) {
                HTTP_OK -> parseSuccess(connection)
                HTTP_CONFLICT -> parseConflict(connection)
                HTTP_REQUEST_TIMEOUT,
                HTTP_TOO_MANY_REQUESTS,
                -> RecoveryRemoteResult.TransientFailure

                in 500..599 -> RecoveryRemoteResult.TransientFailure
                HTTP_FORBIDDEN -> RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.HTTP_403)
                HTTP_NOT_FOUND -> RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.HTTP_404)
                in 300..399 -> RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.HTTP_3XX)
                else -> RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CLIENT_ERROR)
            }
        } catch (_: IOException) {
            RecoveryRemoteResult.TransientFailure
        } catch (_: IllegalArgumentException) {
            RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CLIENT_ERROR)
        } catch (_: SecurityException) {
            RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CLIENT_ERROR)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSuccess(connection: HttpsURLConnection): RecoveryRemoteResult {
        val body = when (val read = connection.inputStream.use(::readBoundedBody)) {
            is BodyReadResult.Body -> read.value
            BodyReadResult.InvalidUtf8,
            BodyReadResult.TooLarge,
            -> return RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CONTRACT_ERROR)
        }

        return when (val parsed = RecoveryResponseParser.parseSuccess(body)) {
            is RecoveryResponseParseResult.Success -> RecoveryRemoteResult.Success(parsed.items)
            RecoveryResponseParseResult.Invalid -> {
                RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CONTRACT_ERROR)
            }
        }
    }

    private fun parseConflict(connection: HttpsURLConnection): RecoveryRemoteResult {
        val errorStream = connection.errorStream
            ?: return RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CONTRACT_ERROR)
        val body = when (val read = errorStream.use(::readBoundedBody)) {
            is BodyReadResult.Body -> read.value
            BodyReadResult.InvalidUtf8,
            BodyReadResult.TooLarge,
            -> return RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CONTRACT_ERROR)
        }

        return if (RecoveryResponseParser.isTooManyPendingConflict(body)) {
            RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.TOO_MANY_PENDING)
        } else {
            RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CONTRACT_ERROR)
        }
    }

    private sealed interface BodyReadResult {
        data class Body(val value: String) : BodyReadResult

        data object TooLarge : BodyReadResult

        data object InvalidUtf8 : BodyReadResult
    }

    private fun readBoundedBody(input: InputStream): BodyReadResult {
        val output = ByteArrayOutputStream(MAX_RESPONSE_BYTES)
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var totalBytes = 0

        while (true) {
            val remainingBytes = MAX_RESPONSE_BYTES - totalBytes
            val readLimit = minOf(buffer.size, remainingBytes.coerceAtLeast(1))
            val bytesRead = input.read(buffer, 0, readLimit)
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

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 10_000
        const val MAX_RESPONSE_BYTES = 1 shl 20
        const val READ_BUFFER_BYTES = 8 * 1024
        const val HTTP_OK = 200
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
    }
}

private fun openHttpsConnection(url: URL): HttpsURLConnection? =
    url.openConnection() as? HttpsURLConnection

internal fun buildRecoveryUrl(baseUrl: String): URL? {
    val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
    val baseUri = runCatching { URI(normalizedBaseUrl) }.getOrNull() ?: return null
    if (!baseUri.scheme.equals("https", ignoreCase = true) ||
        baseUri.host.isNullOrBlank() ||
        baseUri.rawQuery != null ||
        baseUri.rawFragment != null ||
        baseUri.userInfo != null
    ) {
        return null
    }

    return runCatching {
        URL("$normalizedBaseUrl/notifications/pending")
    }.getOrNull()
}
