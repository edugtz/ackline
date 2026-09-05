package com.edu.ackline.recovery

import com.edu.ackline.network.HttpsConnectionFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsRecoveryRemoteClientTest {

    @Test
    fun validZeroItemResponseUsesExpectedBoundedGetConfiguration() {
        val connection = FakeHttpsURLConnection(
            url = URL("https://example.com/notifications/pending"),
            statusCode = 200,
            responseBody = "{\"ok\":true,\"count\":0,\"items\":[]}",
        )
        var requestedUrl: URL? = null

        val result = HttpsRecoveryRemoteClient("https://example.com/") { url ->
            requestedUrl = url
            connection
        }.fetchPending()

        assertEquals(RecoveryRemoteResult.Success(emptyList()), result)
        assertEquals(
            "https://example.com/notifications/pending",
            requestedUrl?.toExternalForm(),
        )
        assertFalse(connection.instanceFollowRedirects)
        assertEquals(10_000, connection.connectTimeout)
        assertEquals(10_000, connection.readTimeout)
        assertEquals("GET", connection.requestMethod)
        assertTrue(connection.doInput)
        assertFalse(connection.doOutput)
        assertFalse(connection.useCaches)
        assertTrue(connection.disconnected)
    }

    @Test
    fun validMultipleItemResponseIsParsed() {
        val result = fetch(
            statusCode = 200,
            responseBody = "{\"ok\":true,\"count\":2,\"items\":[" +
                "{\"v\":\"1\",\"kid\":\"k\",\"nonce\":\"n1\",\"ciphertext\":\"c1\"}," +
                "{\"v\":\"1\",\"kid\":\"k\",\"nonce\":\"n2\",\"ciphertext\":\"c2\"}]}",
        )

        assertTrue(result is RecoveryRemoteResult.Success)
        assertEquals(2, (result as RecoveryRemoteResult.Success).items.size)
    }

    @Test
    fun malformedSuccessfulBodiesArePermanentContractFailures() {
        val bodies = listOf(
            "{\"ok\":true,\"count\":1,\"items\":[]}",
            "{\"ok\":true,\"count\":201,\"items\":[]}",
            "not-json",
            "[]",
            "{\"ok\":true,\"count\":1,\"items\":[{\"v\":\"1\",\"kid\":\"k\",\"nonce\":\"n\"}]}",
            "{\"ok\":true,\"count\":1,\"items\":[{\"v\":\"1\",\"kid\":\"k\",\"nonce\":\"n\",\"ciphertext\":\"c\",\"x\":\"y\"}]}",
            "{\"ok\":true,\"count\":1,\"items\":[{\"v\":\"1\",\"kid\":42,\"nonce\":\"n\",\"ciphertext\":\"c\"}]}",
        )

        bodies.forEach { body ->
            assertEquals(
                RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CONTRACT_ERROR),
                fetch(200, body),
            )
        }
    }

    @Test
    fun oversizedSuccessfulResponseIsAContractFailure() {
        val oversized = "x".repeat((1 shl 20) + 1)

        assertEquals(
            RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CONTRACT_ERROR),
            fetch(200, oversized),
        )
    }

    @Test
    fun mapsTransientHttpStatuses() {
        listOf(408, 429, 500, 503).forEach { statusCode ->
            assertEquals(RecoveryRemoteResult.TransientFailure, fetch(statusCode))
        }
    }

    @Test
    fun mapsPermanentHttpStatusesWithoutFollowingRedirects() {
        assertEquals(
            RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.HTTP_403),
            fetch(403),
        )
        assertEquals(
            RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.HTTP_404),
            fetch(404),
        )
        assertEquals(
            RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.HTTP_3XX),
            fetch(307),
        )
        assertEquals(
            RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CLIENT_ERROR),
            fetch(401),
        )
    }

    @Test
    fun recognizesValidTooManyPendingConflictOnly() {
        assertEquals(
            RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.TOO_MANY_PENDING),
            fetch(
                409,
                responseBody = "",
                errorBody = "{\"ok\":false,\"error\":\"too_many_pending\"}",
            ),
        )
        assertEquals(
            RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CONTRACT_ERROR),
            fetch(
                409,
                responseBody = "",
                errorBody = "{\"ok\":false,\"error\":\"other\"}",
            ),
        )
    }

    @Test
    fun blankAndUnsafeBaseUrlsArePermanentOrNotConfiguredWithoutOpeningConnection() {
        var attempts = 0
        val factory = HttpsConnectionFactory {
            attempts += 1
            error("connection must not be opened")
        }

        assertEquals(
            RecoveryRemoteResult.NotConfigured,
            HttpsRecoveryRemoteClient("  ", factory).fetchPending(),
        )
        listOf(
            "http://example.com",
            "https://",
            "https:///path",
            "https://example.com?query=value",
            "https://example.com#fragment",
            "https://user:password@example.com",
        ).forEach { baseUrl ->
            assertEquals(
                RecoveryRemoteResult.PermanentFailure(RecoveryErrorCategory.CONFIGURATION_ERROR),
                HttpsRecoveryRemoteClient(baseUrl, factory).fetchPending(),
            )
        }
        assertEquals(0, attempts)
    }

    @Test
    fun ioExceptionIsTransient() {
        val result = HttpsRecoveryRemoteClient("https://example.com") {
            throw IOException("test-only network failure")
        }.fetchPending()

        assertEquals(RecoveryRemoteResult.TransientFailure, result)
    }

    private fun fetch(
        statusCode: Int,
        responseBody: String = "",
        errorBody: String? = null,
    ): RecoveryRemoteResult {
        val connection = FakeHttpsURLConnection(
            url = URL("https://example.com/notifications/pending"),
            statusCode = statusCode,
            responseBody = responseBody,
            errorBody = errorBody,
        )
        return HttpsRecoveryRemoteClient("https://example.com") { connection }.fetchPending()
    }

    private class FakeHttpsURLConnection(
        url: URL,
        private val statusCode: Int,
        private val responseBody: String,
        private val errorBody: String? = null,
    ) : HttpsURLConnection(url) {
        var disconnected = false

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = statusCode

        override fun getInputStream(): InputStream {
            if (statusCode >= 400) throw IOException("fake error response")
            return ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))
        }

        override fun getErrorStream(): InputStream? =
            errorBody?.let { ByteArrayInputStream(it.toByteArray(Charsets.UTF_8)) }

        override fun getCipherSuite(): String = "TLS_TEST"

        override fun getLocalCertificates(): Array<Certificate>? = null

        override fun getServerCertificates(): Array<Certificate>? = null

        override fun getPeerPrincipal(): Principal? = null

        override fun getLocalPrincipal(): Principal? = null
    }
}
