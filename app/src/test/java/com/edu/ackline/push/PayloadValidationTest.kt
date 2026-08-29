package com.edu.ackline.push

import com.edu.ackline.notifications.AcklineNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PayloadValidationTest {

    @Test
    fun acceptsEachSupportedLevel() {
        listOf("remember", "important", "urgent").forEach { level ->
            assertNotNull(parseAcklinePayload(validData(level)))
        }
    }

    @Test
    fun rejectsMissingOrBlankRequiredFields() {
        val requiredKeys = listOf("notification_id", "level", "title", "message", "sent_at")

        requiredKeys.forEach { key ->
            assertNull(parseAcklinePayload(validData("important") - key))
            assertNull(parseAcklinePayload(validData("important") + (key to " ")))
        }
    }

    @Test
    fun rejectsUnsupportedLevels() {
        assertNull(parseAcklinePayload(validData("REMEMBER")))
        assertNull(parseAcklinePayload(validData("unknown")))
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
            "notification_id" to "test-001",
            "level" to level,
            "title" to "Ackline test",
            "message" to "Non-sensitive Phase 1 test",
            "sent_at" to "2026-08-29T12:00:00Z",
        )
}
