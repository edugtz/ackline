package com.edu.ackline.ack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
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
            HttpsAckRemoteClient("").acknowledge("test-id", "test-token"),
        )
        assertEquals(
            AckRemoteResult.NotConfigured,
            HttpsAckRemoteClient("  ").acknowledge("test-id", "test-token"),
        )
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
    fun rejectsAckTokensContainingHeaderLineBreaksBeforeRequest() {
        listOf("token\rvalue", "token\nvalue", "token\r\nvalue").forEach { token ->
            assertEquals(
                AckRemoteResult.PermanentFailure(AckErrorCategory.CLIENT_ERROR),
                HttpsAckRemoteClient("https://example.com").acknowledge("test-id", token),
            )
        }
    }
}
