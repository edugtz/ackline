package com.edu.ackline.push

import com.edu.ackline.notifications.AcklineNotificationManager
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PayloadValidationTest {

    @Test
    fun acceptsValidProtocolV1EnvelopeForEachSupportedLevel() {
        listOf("remember", "important", "urgent").forEach { level ->
            val envelope = parseAcklinePayload(validData(level))
            assertNotNull(envelope)
            assertEquals(1, envelope?.protocolVersion)
            assertEquals(level, envelope?.level?.wireValue)
        }
    }

    @Test
    fun rejectsMissingOrBlankRequiredFields() {
        val requiredKeys = listOf(
            "protocol",
            "notification_id",
            "level",
            "title",
            "message",
            "created_at",
        )

        requiredKeys.forEach { key ->
            assertNull(parseAcklinePayload(validData("important") - key))
            assertNull(parseAcklinePayload(validData("important") + (key to " ")))
        }
    }

    @Test
    fun rejectsUnsupportedProtocol() {
        assertNull(parseAcklinePayload(validData("important") + ("protocol" to "2")))
    }

    @Test
    fun rejectsUnsupportedLevels() {
        assertNull(parseAcklinePayload(validData("REMEMBER")))
        assertNull(parseAcklinePayload(validData("unknown")))
    }

    @Test
    fun rejectsInvalidCreatedAt() {
        assertNull(parseAcklinePayload(validData("important") + ("created_at" to "not-an-instant")))
    }

    @Test
    fun parsesValidCreatedAtAsInstant() {
        val createdAt = "2026-08-29T12:00:00Z"
        val envelope = parseAcklinePayload(
            validData("important") + ("created_at" to createdAt),
        )

        assertEquals(Instant.parse(createdAt), envelope?.createdAt)
    }

    @Test
    fun mapsLevelsToStableNotificationChannelsAndImportance() {
        assertEquals(
            AcklineNotificationManager.REMEMBER_CHANNEL_ID,
            AcklineNotificationManager.channelIdForLevel("remember"),
        )
        assertEquals(
            AcklineNotificationManager.IMPORTANT_CHANNEL_ID,
            AcklineNotificationManager.channelIdForLevel("important"),
        )
        assertEquals(
            AcklineNotificationManager.URGENT_CHANNEL_ID,
            AcklineNotificationManager.channelIdForLevel("urgent"),
        )
        assertEquals(
            android.app.NotificationManager.IMPORTANCE_LOW,
            AcklineNotificationManager.channelImportanceForLevel("remember"),
        )
        assertEquals(
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
            AcklineNotificationManager.channelImportanceForLevel("important"),
        )
        assertEquals(
            android.app.NotificationManager.IMPORTANCE_HIGH,
            AcklineNotificationManager.channelImportanceForLevel("urgent"),
        )
        assertNull(AcklineNotificationManager.channelIdForLevel("unknown"))
    }

    private fun validData(level: String): Map<String, String> =
        mapOf(
            "protocol" to "1",
            "notification_id" to "test-001",
            "level" to level,
            "title" to "Ackline test",
            "message" to "Non-sensitive Phase 2 test",
            "created_at" to "2026-08-29T12:00:00Z",
        )
}
