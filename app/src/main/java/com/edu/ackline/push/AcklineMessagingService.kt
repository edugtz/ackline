package com.edu.ackline.push

import android.util.Log
import com.edu.ackline.SetupState
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Phase 0 push boundary.
 *
 * - [onRegistered] receives the current Firebase Installation ID (FID) from the
 *   FID-based registration flow (firebase_messaging_installation_id_enabled).
 *   The FID is operational data: surfaced to the setup screen, never logged.
 * - [onMessageReceived] handles fake data-only test messages only.
 *
 * Firebase-specific types (RemoteMessage) terminate here.
 */
class AcklineMessagingService : FirebaseMessagingService() {

    override fun onRegistered(installationId: String) {
        Log.i(TAG, "FCM registration complete")
        SetupState.onRegistered(installationId)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val notificationId = data["notification_id"]
        val title = data["title"]
        val message = data["message"]

        // Phase 0 payload validation: all three fields must be present and
        // non-blank. Invalid payloads are rejected generically — no crash and
        // no payload contents in logs.
        if (notificationId.isNullOrBlank() || title.isNullOrBlank() || message.isNullOrBlank()) {
            Log.w(TAG, "invalid Phase 0 data message")
            SetupState.onMessageReceived("Invalid test message")
            return
        }

        Log.i(TAG, "fake data message received: notification_id=$notificationId")
        SetupState.onMessageReceived("#$notificationId · $title — $message")
    }

    private companion object {
        const val TAG = "AcklinePush"
    }
}