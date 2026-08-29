package com.edu.ackline.push

import android.util.Log
import com.edu.ackline.SetupState
import com.edu.ackline.notifications.AcklineNotificationManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.time.Instant

internal data class AcklinePayload(
    val notificationId: String,
    val level: String,
    val title: String,
    val message: String,
    val sentAt: String,
)

internal fun parseAcklinePayload(data: Map<String, String>): AcklinePayload? {
    val notificationId = data["notification_id"] ?: return null
    val level = data["level"] ?: return null
    val title = data["title"] ?: return null
    val message = data["message"] ?: return null
    val sentAt = data["sent_at"] ?: return null

    if (
        notificationId.isBlank() ||
        level !in setOf("remember", "important", "urgent") ||
        title.isBlank() ||
        message.isBlank() ||
        sentAt.isBlank()
    ) {
        return null
    }

    return AcklinePayload(
        notificationId = notificationId,
        level = level,
        title = title,
        message = message,
        sentAt = sentAt,
    )
}

/**
 * Phase 1 push boundary.
 *
 * - [onRegistered] receives the current Firebase Installation ID (FID) from the
 *   FID-based registration flow (firebase_messaging_installation_id_enabled).
 *   The FID is operational data: surfaced to the setup screen, never logged.
 * - [onMessageReceived] handles fake data-only Phase 1 test messages.
 *
 * Firebase-specific types (RemoteMessage) terminate here.
 */
class AcklineMessagingService : FirebaseMessagingService() {

    override fun onRegistered(installationId: String) {
        Log.i(TAG, "FCM registration complete")
        SetupState.onRegistered(installationId)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val payload = parseAcklinePayload(remoteMessage.data)
        if (payload == null) {
            Log.w(TAG, "invalid Phase 1 data message")
            SetupState.onMessageReceived("Invalid test message")
            return
        }

        val receivedAt = Instant.now().toString()
        val displayed = AcklineNotificationManager.show(
            context = applicationContext,
            notificationId = payload.notificationId,
            level = payload.level,
            title = payload.title,
            message = payload.message,
        )
        val displayedAt = if (displayed) Instant.now().toString() else "not_posted"

        SetupState.onMessageReceived(
            "#${payload.notificationId} · ${payload.title} — ${payload.message}",
        )
        Log.i(
            TAG,
            "data message handled: " +
                "notification_id=${payload.notificationId.forDiagnosticLog()} " +
                "level=${payload.level} " +
                "sent_at=${payload.sentAt.forDiagnosticLog()} " +
                "received_at=$receivedAt " +
                "displayed_at=$displayedAt",
        )
    }

    private companion object {
        const val TAG = "AcklinePush"

        private fun String.forDiagnosticLog(): String =
            take(MAX_DIAGNOSTIC_VALUE_LENGTH)
                .replace('\n', ' ')
                .replace('\r', ' ')

        const val MAX_DIAGNOSTIC_VALUE_LENGTH = 128
    }
}
