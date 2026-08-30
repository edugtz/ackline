package com.edu.ackline.ack

import android.content.Context
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
) {

    private val appContext = context.applicationContext

    fun acknowledge(
        notificationId: String,
        acknowledgedAt: Instant = Instant.now(),
    ): LocalAcknowledgmentResult {
        val updatedRows = repository.acknowledge(notificationId, acknowledgedAt)
        if (updatedRows == 1) {
            AcklineNotificationManager.cancel(appContext, notificationId)
            return LocalAcknowledgmentResult.ACKNOWLEDGED
        }

        if (repository.findById(notificationId) != null) {
            AcklineNotificationManager.cancel(appContext, notificationId)
            return LocalAcknowledgmentResult.ALREADY_ACKNOWLEDGED
        }

        return LocalAcknowledgmentResult.NOT_FOUND
    }
}
