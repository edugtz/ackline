package com.edu.ackline.ack

import java.io.IOException
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import com.edu.ackline.network.HttpsConnectionFactory

class HttpsAckRemoteClient(
    private val ackBaseUrl: String,
    private val connectionFactory: HttpsConnectionFactory,
) : AckRemoteClient {

    override fun acknowledge(
        notificationId: String,
        ackToken: String,
    ): AckRemoteResult {
        if (ackBaseUrl.isBlank()) {
            return AckRemoteResult.NotConfigured
        }

        if (ackToken.isBlank() || ackToken.any { it == '\r' || it == '\n' }) {
            return AckRemoteResult.PermanentFailure(AckErrorCategory.CLIENT_ERROR)
        }

        val url = buildAckUrl(ackBaseUrl, notificationId)
            ?: return AckRemoteResult.PermanentFailure(AckErrorCategory.CLIENT_ERROR)

        val connection = try {
            connectionFactory.open(url)
        } catch (_: IOException) {
            return AckRemoteResult.TransientFailure
        } catch (_: IllegalArgumentException) {
            return AckRemoteResult.PermanentFailure(AckErrorCategory.CLIENT_ERROR)
        }

        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = "POST"
            connection.doInput = true
            connection.doOutput = false
            connection.useCaches = false
            connection.setRequestProperty("X-Ack-Token", ackToken)

            classifyAckHttpStatus(connection.responseCode)
        } catch (_: IOException) {
            AckRemoteResult.TransientFailure
        } catch (_: IllegalArgumentException) {
            AckRemoteResult.PermanentFailure(AckErrorCategory.CLIENT_ERROR)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 10_000
    }
}

internal fun buildAckUrl(baseUrl: String, notificationId: String): URL? {
    if (notificationId.isBlank()) return null

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
        URL("$normalizedBaseUrl/ack/${encodeAckPathSegment(notificationId)}")
    }.getOrNull()
}

internal fun encodeAckPathSegment(value: String): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    return buildString(bytes.size) {
        bytes.forEach { byte ->
            val unsigned = byte.toInt() and 0xFF
            if (unsigned.isUnreservedAckPathByte()) {
                append(unsigned.toChar())
            } else {
                append('%')
                append(ACK_PATH_HEX_DIGITS[unsigned ushr 4])
                append(ACK_PATH_HEX_DIGITS[unsigned and 0x0F])
            }
        }
    }
}

private const val ACK_PATH_HEX_DIGITS = "0123456789ABCDEF"

private fun Int.isUnreservedAckPathByte(): Boolean =
    this in 'A'.code..'Z'.code ||
        this in 'a'.code..'z'.code ||
        this in '0'.code..'9'.code ||
        this == '-'.code ||
        this == '_'.code ||
        this == '~'.code

internal fun classifyAckHttpStatus(statusCode: Int): AckRemoteResult =
    when {
        statusCode == 200 -> AckRemoteResult.Success
        statusCode == 408 || statusCode == 429 || statusCode in 500..599 -> {
            AckRemoteResult.TransientFailure
        }

        statusCode == 400 -> AckRemoteResult.PermanentFailure(AckErrorCategory.HTTP_400)
        statusCode == 403 -> AckRemoteResult.PermanentFailure(AckErrorCategory.HTTP_403)
        statusCode == 404 -> AckRemoteResult.PermanentFailure(AckErrorCategory.HTTP_404)
        statusCode in 300..399 -> AckRemoteResult.PermanentFailure(AckErrorCategory.HTTP_3XX)
        statusCode in 400..499 -> AckRemoteResult.PermanentFailure(AckErrorCategory.CLIENT_ERROR)
        else -> AckRemoteResult.PermanentFailure(AckErrorCategory.CLIENT_ERROR)
    }
