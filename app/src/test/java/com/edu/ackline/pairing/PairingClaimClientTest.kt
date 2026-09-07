package com.edu.ackline.pairing

import com.edu.ackline.network.HttpsConnectionFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.security.Principal
import java.security.cert.Certificate
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingClaimClientTest {

    @Test
    fun postsValidH1RequestToExplicitEndpointAndParsesTypedSuccess() {
        val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val connection = FakeHttpsURLConnection(
            url = URL("https://example.com/pairing/claim"),
            statusCode = 200,
            responseBody = """
                {"ok":true,"kid":"ackline-main","e2ee_key_b64":"$key","ack_base_url":"https://hermes.example/api/"}
            """.trimIndent(),
        )
        var requestedUrl: URL? = null

        val result = PairingClaimClient(HttpsConnectionFactory { url ->
            requestedUrl = url
            connection
        }).claim(
            pairingEndpoint = "https://hermes.example/pairing/claim",
            sessionId = "session-001",
            token = "token-001",
            fid = "fid-001",
        )

        assertEquals(
            PairingClaimResult.Success(
                PairingClaimResponse(
                    kid = "ackline-main",
                    e2eeKeyB64 = key,
                    ackBaseUrl = "https://hermes.example/api",
                ),
            ),
            result,
        )
        assertEquals(
            "https://hermes.example/pairing/claim",
            requestedUrl?.toExternalForm(),
        )
        val request = JSONObject(connection.requestBody.toString(Charsets.UTF_8))
        assertEquals("session-001", request.getString("session_id"))
        assertEquals("token-001", request.getString("token"))
        assertEquals("fid-001", request.getString("fid"))
        assertEquals("application/json", connection.getRequestProperty("Content-Type"))
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertFalse(connection.instanceFollowRedirects)
        assertEquals(10_000, connection.connectTimeout)
        assertEquals(10_000, connection.readTimeout)
        assertEquals("POST", connection.requestMethod)
        assertTrue(connection.doInput)
        assertTrue(connection.doOutput)
        assertTrue(connection.disconnected)
    }

    @Test
    fun malformedMissingAndInvalidSuccessFieldsFailClosed() {
        val key = Base64.getEncoder().encodeToString(ByteArray(32))
        val bodies = listOf(
            "not-json",
            "{\"ok\":true,\"kid\":\"ackline-main\",\"e2ee_key_b64\":\"$key\"}",
            "{\"ok\":true,\"kid\":\"bad kid\",\"e2ee_key_b64\":\"$key\",\"ack_base_url\":\"https://example.com\"}",
            "{\"ok\":true,\"kid\":\"ackline-main\",\"e2ee_key_b64\":\"not-base64!\",\"ack_base_url\":\"https://example.com\"}",
            "{\"ok\":true,\"kid\":\"ackline-main\",\"e2ee_key_b64\":\"$key\",\"ack_base_url\":\"http://example.com\"}",
        )

        bodies.forEach { body ->
            val result = clientFor(statusCode = 200, responseBody = body)
                .claim("https://example.com/pairing/claim", "s", "t", "f")

            assertEquals(
                PairingClaimResult.Failure(PairingClaimFailure.MALFORMED_RESPONSE),
                result,
            )
        }
    }

    @Test
    fun invalidPairingEndpointsAreRejectedBeforeConnectionCreation() {
        var attempts = 0
        val client = PairingClaimClient(HttpsConnectionFactory {
            attempts += 1
            error("connection must not be opened")
        })

        listOf(
            "",
            "http://example.com/pairing/claim",
            "https://",
            "https:///pairing/claim",
            "https://example.com/pairing/claim?token=secret",
            "https://example.com/pairing/claim#fragment",
            "https://user:password@example.com/pairing/claim",
        ).forEach { endpoint ->
            assertEquals(
                PairingClaimResult.Failure(PairingClaimFailure.INVALID_ENDPOINT),
                client.claim(endpoint, "s", "t", "f"),
            )
        }
        assertEquals(0, attempts)
    }

    @Test
    fun invalidLocalClaimValuesAreRejectedWithoutOpeningConnection() {
        var attempts = 0
        val client = PairingClaimClient(HttpsConnectionFactory {
            attempts += 1
            error("connection must not be opened")
        })

        assertEquals(
            PairingClaimResult.Failure(PairingClaimFailure.INVALID_REQUEST),
            client.claim("https://example.com/pairing/claim", " ", "token", "fid"),
        )
        assertEquals(
            PairingClaimResult.Failure(PairingClaimFailure.INVALID_REQUEST),
            client.claim("https://example.com/pairing/claim", "session", "token\n", "fid"),
        )
        assertEquals(0, attempts)
    }

    @Test
    fun mapsKnownH1FailuresWithoutExposingResponseBody() {
        val cases = listOf(
            Triple(400, "invalid_request", PairingClaimFailure.INVALID_REQUEST),
            Triple(403, "invalid", PairingClaimFailure.INVALID),
            Triple(403, "expired", PairingClaimFailure.EXPIRED),
            Triple(403, "consumed", PairingClaimFailure.CONSUMED),
            Triple(403, "tailscale_identity_required", PairingClaimFailure.TAILSCALE_IDENTITY_REQUIRED),
            Triple(409, "replace_required", PairingClaimFailure.REPLACE_REQUIRED),
            Triple(429, "rate_limited", PairingClaimFailure.RATE_LIMITED),
            Triple(500, "server_misconfigured", PairingClaimFailure.SERVER_MISCONFIGURED),
        )

        cases.forEach { (statusCode, errorCode, expectedFailure) ->
            val secret = "token-secret-value"
            val result = clientFor(
                statusCode = statusCode,
                responseBody = "",
                errorBody = "{\"ok\":false,\"error\":\"$errorCode\",\"detail\":\"$secret\"}",
            ).claim("https://example.com/pairing/claim", "s", secret, "fid")

            assertEquals(
                PairingClaimResult.Failure(expectedFailure),
                result,
            )
            assertFalse(result.toString().contains(secret))
        }
    }

    @Test
    fun malformedKnownFailureBodyFailsClosed() {
        assertEquals(
            PairingClaimResult.Failure(PairingClaimFailure.MALFORMED_RESPONSE),
            clientFor(403, responseBody = "", errorBody = "not-json")
                .claim("https://example.com/pairing/claim", "s", "t", "f"),
        )
        assertEquals(
            PairingClaimResult.Failure(PairingClaimFailure.UNEXPECTED_STATUS),
            clientFor(401, responseBody = "", errorBody = "")
                .claim("https://example.com/pairing/claim", "s", "t", "f"),
        )
    }

    @Test
    fun oversizedResponseIsRejectedAndSecretsStayOutOfTypedObjects() {
        val oversized = "x".repeat(64 * 1024 + 1)
        val secret = "pairing-token-secret"
        val result = clientFor(200, responseBody = oversized)
            .claim("https://example.com/pairing/claim", "session", secret, "fid")

        assertEquals(
            PairingClaimResult.Failure(PairingClaimFailure.RESPONSE_TOO_LARGE),
            result,
        )
        assertFalse(PairingClaimRequest("session", secret, "fid").toString().contains(secret))
        assertFalse(result.toString().contains(secret))
    }

    @Test
    fun connectionFailureIsTypedAndDoesNotRetry() {
        var attempts = 0
        val result = PairingClaimClient(HttpsConnectionFactory {
            attempts += 1
            throw IOException("test-only network failure")
        }).claim("https://example.com/pairing/claim", "s", "t", "f")

        assertEquals(
            PairingClaimResult.Failure(PairingClaimFailure.TRANSPORT),
            result,
        )
        assertEquals(1, attempts)
    }

    private fun clientFor(
        statusCode: Int,
        responseBody: String,
        errorBody: String? = null,
    ): PairingClaimClient {
        val connection = FakeHttpsURLConnection(
            url = URL("https://example.com/pairing/claim"),
            statusCode = statusCode,
            responseBody = responseBody,
            errorBody = errorBody,
        )
        return PairingClaimClient(HttpsConnectionFactory { connection })
    }

    private class FakeHttpsURLConnection(
        url: URL,
        private val statusCode: Int,
        private val responseBody: String,
        private val errorBody: String? = null,
    ) : HttpsURLConnection(url) {
        val requestBody = ByteArrayOutputStream()
        var disconnected = false

        private val requestProperties = mutableMapOf<String, String>()

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = statusCode

        override fun getOutputStream(): OutputStream = requestBody

        override fun setRequestProperty(key: String, value: String) {
            requestProperties[key] = value
        }

        override fun getRequestProperty(key: String): String? = requestProperties[key]

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
