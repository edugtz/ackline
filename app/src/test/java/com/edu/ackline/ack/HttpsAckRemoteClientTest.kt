package com.edu.ackline.ack

import com.edu.ackline.network.HttpsConnectionFactory
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsAckRemoteClientTest {

    @Test
    fun classifiesHermesAckStatuses() {
        assertEquals(AckRemoteResult.Success, classifyAckHttpStatus(200))
        assertEquals(AckRemoteResult.TransientFailure, classifyAckHttpStatus(408))
        assertEquals(AckRemoteResult.TransientFailure, classifyAckHttpStatus(429))
        assertEquals(AckRemoteResult.TransientFailure, classifyAckHttpStatus(503))
        assertEquals(
            AckRemoteResult.PermanentFailure(AckErrorCategory.HTTP_400),
            classifyAckHttpStatus(400),
        )
        assertEquals(
            AckRemoteResult.PermanentFailure(AckErrorCategory.HTTP_403),
            classifyAckHttpStatus(403),
        )
        assertEquals(
            AckRemoteResult.PermanentFailure(AckErrorCategory.HTTP_404),
            classifyAckHttpStatus(404),
        )
        assertEquals(
            AckRemoteResult.PermanentFailure(AckErrorCategory.HTTP_3XX),
            classifyAckHttpStatus(307),
        )
        assertEquals(
            AckRemoteResult.PermanentFailure(AckErrorCategory.CLIENT_ERROR),
            classifyAckHttpStatus(401),
        )
    }

    @Test
    fun missingBaseUrlDoesNotAttemptInsecureOrUnconfiguredRequest() {
        assertEquals(
            AckRemoteResult.NotConfigured,
            HttpsAckRemoteClient("", unusedConnectionFactory()).acknowledge(
                "test-id",
                "test-token",
            ),
        )
        assertEquals(
            AckRemoteResult.NotConfigured,
            HttpsAckRemoteClient("  ", unusedConnectionFactory()).acknowledge(
                "test-id",
                "test-token",
            ),
        )
    }

    @Test
    fun usesSuppliedConnectionFactoryAndPreservesPostConfiguration() {
        val connection = FakeHttpsURLConnection(
            url = URL("https://example.com/ack/test-id"),
            statusCode = 200,
        )
        var requestedUrl: URL? = null

        val result = HttpsAckRemoteClient(
            "https://example.com/",
            HttpsConnectionFactory { url ->
                requestedUrl = url
                connection
            },
        ).acknowledge("test-id", "test-token")

        assertEquals(AckRemoteResult.Success, result)
        assertEquals("https://example.com/ack/test-id", requestedUrl?.toExternalForm())
        assertFalse(connection.instanceFollowRedirects)
        assertEquals(10_000, connection.connectTimeout)
        assertEquals(10_000, connection.readTimeout)
        assertEquals("POST", connection.requestMethod)
        assertTrue(connection.doInput)
        assertFalse(connection.doOutput)
        assertFalse(connection.useCaches)
        assertEquals("test-token", connection.getRequestProperty("X-Ack-Token"))
        assertTrue(connection.disconnected)
    }

    @Test
    fun encodesNotificationIdAsOneSafeUtf8PathSegment() {
        val expectedEncoding = mapOf(
            "/" to "%2F",
            "%" to "%25",
            ".." to "%2E%2E",
            "space here" to "space%20here",
            "?" to "%3F",
            "á/猫" to "%C3%A1%2F%E7%8C%AB",
        )

        expectedEncoding.forEach { (notificationId, encodedId) ->
            val url = buildAckUrl("https://example.com", notificationId)

            assertNotNull(url)
            assertEquals("https://example.com/ack/$encodedId", url?.toExternalForm())
            assertFalse(url?.query != null)
            assertFalse(url?.ref != null)
        }
    }

    @Test
    fun rejectsUnsafeAckBaseUrls() {
        listOf(
            "http://example.com",
            "https://",
            "https:///path",
            "https://example.com?query=value",
            "https://example.com#fragment",
            "https://user:password@example.com",
        ).forEach { baseUrl ->
            assertNull(buildAckUrl(baseUrl, "test-id"))
        }
    }

    @Test
    fun blankAndUnsafeBaseUrlsFailBeforeConnectionCreation() {
        var attempts = 0
        val factory = HttpsConnectionFactory {
            attempts += 1
            error("connection must not be opened")
        }

        assertEquals(
            AckRemoteResult.NotConfigured,
            HttpsAckRemoteClient("  ", factory).acknowledge("test-id", "test-token"),
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
                AckRemoteResult.PermanentFailure(AckErrorCategory.CLIENT_ERROR),
                HttpsAckRemoteClient(baseUrl, factory).acknowledge("test-id", "test-token"),
            )
        }
        assertEquals(0, attempts)
    }

    @Test
    fun connectionFactoryIOExceptionIsTransient() {
        val result = HttpsAckRemoteClient(
            "https://example.com",
            HttpsConnectionFactory { throw IOException("test-only network failure") },
        ).acknowledge("test-id", "test-token")

        assertEquals(AckRemoteResult.TransientFailure, result)
    }

    @Test
    fun rejectsAckTokensContainingHeaderLineBreaksBeforeRequest() {
        listOf("token\rvalue", "token\nvalue", "token\r\nvalue").forEach { token ->
            assertEquals(
                AckRemoteResult.PermanentFailure(AckErrorCategory.CLIENT_ERROR),
                HttpsAckRemoteClient("https://example.com", unusedConnectionFactory())
                    .acknowledge("test-id", token),
            )
        }
    }

    private fun unusedConnectionFactory(): HttpsConnectionFactory =
        HttpsConnectionFactory { error("connection must not be opened") }

    private class FakeHttpsURLConnection(
        url: URL,
        private val statusCode: Int,
    ) : HttpsURLConnection(url) {
        var disconnected = false

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = statusCode

        override fun getCipherSuite(): String = "TLS_TEST"

        override fun getLocalCertificates(): Array<Certificate>? = null

        override fun getServerCertificates(): Array<Certificate>? = null

        override fun getPeerPrincipal(): Principal? = null

        override fun getLocalPrincipal(): Principal? = null

        override fun getInputStream(): InputStream = error("input stream must not be read")
    }
}
