package com.edu.ackline.ack

import android.content.Context
import android.util.Log
import com.edu.ackline.data.AlertRepository
import com.edu.ackline.notifications.AcklineNotificationManager
import java.time.Instant

enum class LocalAcknowledgmentResult {
    ACKNOWLEDGED,
    ALREADY_ACKNOWLEDGED,
    NOT_FOUND,
}

class LocalAcknowledgmentManager(
    context: Context,
    private val repository: AlertRepository,
    private val ackSyncScheduler: AckSyncScheduler,
) {

    private val appContext = context.applicationContext

    fun acknowledge(
        notificationId: String,
        acknowledgedAt: Instant = Instant.now(),
    ): LocalAcknowledgmentResult {
        val updatedRows = repository.acknowledge(notificationId, acknowledgedAt)
        if (updatedRows == 1) {
            AcklineNotificationManager.cancel(appContext, notificationId)
            enqueueRemoteSync()
            return LocalAcknowledgmentResult.ACKNOWLEDGED
        }

        if (repository.findById(notificationId) != null) {
            AcklineNotificationManager.cancel(appContext, notificationId)
            enqueueRemoteSync()
            return LocalAcknowledgmentResult.ALREADY_ACKNOWLEDGED
        }

        return LocalAcknowledgmentResult.NOT_FOUND
    }

    private fun enqueueRemoteSync() {
        try {
            ackSyncScheduler.enqueue()
        } catch (_: Exception) {
            Log.e(TAG, "ACK sync scheduling failed")
        }
    }

    private companion object {
        const val TAG = "AcklineAckSync"
    }
}
