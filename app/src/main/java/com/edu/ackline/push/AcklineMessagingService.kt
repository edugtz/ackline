package com.edu.ackline.push

import android.util.Log
import com.edu.ackline.AcklineApplication
import com.edu.ackline.BuildConfig
import com.edu.ackline.SetupState
import com.edu.ackline.security.PayloadCrypto
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
 * - [onMessageReceived] accepts only the encrypted Phase 5 FCM envelope.
 *
 * Firebase-specific types (RemoteMessage) terminate here.
 */
class AcklineMessagingService : FirebaseMessagingService() {

    override fun onRegistered(installationId: String) {
        Log.i(TAG, "FCM registration complete")
        SetupState.onRegistered(installationId)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val encryptedEnvelope = when (val parsed = EncryptedPushEnvelope.parse(remoteMessage.data)) {
            is EncryptedPushEnvelope.ParseResult.Success -> parsed.envelope
            is EncryptedPushEnvelope.ParseResult.Rejected -> {
                reject(parsed.failure.name.lowercase())
                return
            }
        }
        if (encryptedEnvelope.kid != BuildConfig.PAYLOAD_ENCRYPTION_KID) {
            reject("unknown_kid")
            return
        }

        val plaintext = when (val decrypted = acklineApplication.payloadCrypto.decrypt(encryptedEnvelope)) {
            is PayloadCrypto.DecryptResult.Success -> decrypted.plaintext
            is PayloadCrypto.DecryptResult.Rejected -> {
                reject(decrypted.failure.name.lowercase())
                return
            }
        }
        val innerData = InnerPayloadDecoder.decode(plaintext)
        if (innerData == null) {
            reject("invalid_inner_payload")
            return
        }
        val envelope = parseAcklinePayload(innerData)
        if (envelope == null) {
            reject("invalid_inner_payload")
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

                    SetupState.onMessageReceived("Encrypted alert received")
                    Log.i(TAG, "encrypted alert persisted; notification_posted=$displayed")
                }

                AlertRepository.InsertResult.DUPLICATE -> {
                    SetupState.onMessageReceived("Duplicate encrypted alert ignored")
                    Log.i(TAG, "duplicate encrypted alert ignored")
                }
            }
        } catch (_: Exception) {
            Log.e(TAG, "encrypted alert persistence failed")
            SetupState.onMessageReceived("Alert could not be persisted")
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
