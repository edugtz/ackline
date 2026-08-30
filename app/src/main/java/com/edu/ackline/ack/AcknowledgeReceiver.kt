package com.edu.ackline.ack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.edu.ackline.AcklineApplication

class AcknowledgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACKNOWLEDGE) return

        val notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID)
        if (notificationId.isNullOrBlank()) {
            Log.w(TAG, "notification acknowledgment ignored: missing notification_id")
            return
        }

        val pendingResult = goAsync()
        val application = context.applicationContext as? AcklineApplication
        if (application == null) {
            Log.e(TAG, "notification acknowledgment failed: invalid application")
            pendingResult.finish()
            return
        }

        try {
            application.acknowledgmentExecutor.execute {
                try {
                    application.localAcknowledgmentManager.acknowledge(notificationId)
                } catch (_: Exception) {
                    Log.e(TAG, "notification acknowledgment failed")
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (_: Exception) {
            Log.e(TAG, "notification acknowledgment scheduling failed")
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "com.edu.ackline.action.ACKNOWLEDGE"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        private const val TAG = "AcklineAck"
    }
}
