package com.edu.ackline.push

import android.util.Log
import com.edu.ackline.AcklineApplication
import com.edu.ackline.SetupState
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase push boundary.
 *
 * - [onRegistered] receives the current Firebase Installation ID (FID) from the
 *   FID-based registration flow (firebase_messaging_installation_id_enabled).
 *   The FID is operational data: surfaced to the setup screen, never logged.
 * - [onMessageReceived] accepts only the encrypted Phase 5 FCM envelope.
 *
 * Firebase-specific types (RemoteMessage) terminate here.
 */
class AcklineMessagingService : FirebaseMessagingService() {

    override fun onRegistered(installationId: String) {
        Log.i(TAG, "FCM registration complete")
        acklineApplication.fidRePairManager.onRegistered(installationId)
    }

    override fun onDeletedMessages() {
        acklineApplication.recoveryTriggers.onDeletedMessages()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        when (val result = acklineApplication.alertIngestion.ingest(remoteMessage.data)) {
            is AlertIngestionResult.Inserted -> {
                    SetupState.onMessageReceived("Encrypted alert received")
                    Log.i(
                        TAG,
                        "encrypted alert persisted; " +
                            "notification_posted=${result.notificationPosted}",
                    )
            }

            AlertIngestionResult.Duplicate -> {
                    SetupState.onMessageReceived("Duplicate encrypted alert ignored")
                    Log.i(TAG, "duplicate encrypted alert ignored")
            }

            is AlertIngestionResult.Rejected -> reject(result.category.diagnosticValue)

            AlertIngestionResult.PersistenceFailure -> {
                Log.e(TAG, "encrypted alert persistence failed")
                SetupState.onMessageReceived("Alert could not be persisted")
            }
        }
    }

    private val acklineApplication: AcklineApplication
        get() = applicationContext as AcklineApplication

    private companion object {
        const val TAG = "AcklinePush"
    }

    private fun reject(category: String) {
        Log.w(TAG, "encrypted payload rejected: $category")
        SetupState.onMessageReceived("Encrypted payload rejected")
    }
}
