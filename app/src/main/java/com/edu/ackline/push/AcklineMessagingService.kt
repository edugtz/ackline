package com.edu.ackline.push

import android.util.Log
import com.edu.ackline.AcklineApplication
import com.edu.ackline.SetupState
import com.edu.ackline.data.AlertRepository
import com.edu.ackline.notifications.AcklineNotificationManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase push boundary.
 *
 * - [onRegistered] receives the current Firebase Installation ID (FID) from the
 *   FID-based registration flow (firebase_messaging_installation_id_enabled).
 *   The FID is operational data: surfaced to the setup screen, never logged.
 * - [onMessageReceived] handles fake data-only Phase 2 test messages.
 *
 * Firebase-specific types (RemoteMessage) terminate here.
 */
class AcklineMessagingService : FirebaseMessagingService() {

    override fun onRegistered(installationId: String) {
        Log.i(TAG, "FCM registration complete")
        SetupState.onRegistered(installationId)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val envelope = parseAcklinePayload(remoteMessage.data)
        if (envelope == null) {
            Log.w(TAG, "invalid Phase 2 data message")
            SetupState.onMessageReceived("Invalid test message")
            return
        }

        try {
            when (acklineApplication.alertRepository.insertIncoming(envelope)) {
                AlertRepository.InsertResult.INSERTED -> {
                    val displayed = AcklineNotificationManager.show(
                        context = applicationContext,
                        notificationId = envelope.notificationId,
                        level = envelope.level,
                        title = envelope.title,
                        message = envelope.message,
                    )

                    SetupState.onMessageReceived(
                        "#${envelope.notificationId} · ${envelope.title} — ${envelope.message}",
                    )
                    Log.i(
                        TAG,
                        "data message persisted: " +
                            "notification_id=${envelope.notificationId.forDiagnosticLog()} " +
                            "level=${envelope.level.wireValue} " +
                            "notification_posted=$displayed",
                    )
                }

                AlertRepository.InsertResult.DUPLICATE -> {
                    SetupState.onMessageReceived(
                        "#${envelope.notificationId} · ${envelope.title} — ${envelope.message}",
                    )
                    Log.i(
                        TAG,
                        "duplicate data message ignored: " +
                            "notification_id=${envelope.notificationId.forDiagnosticLog()}",
                    )
                }
            }
        } catch (_: Exception) {
            Log.e(TAG, "data message persistence failed")
            SetupState.onMessageReceived("Alert could not be persisted")
        }
    }

    private val acklineApplication: AcklineApplication
        get() = applicationContext as AcklineApplication

    private companion object {
        const val TAG = "AcklinePush"

        private fun String.forDiagnosticLog(): String =
            take(MAX_DIAGNOSTIC_VALUE_LENGTH)
                .replace('\n', ' ')
                .replace('\r', ' ')

        const val MAX_DIAGNOSTIC_VALUE_LENGTH = 128
    }
}
