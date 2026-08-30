package com.edu.ackline.ack

data class PendingAcknowledgment(
    val notificationId: String,
    val acknowledgedAtEpochMillis: Long,
    val ackToken: String?,
)

interface AckSyncStore {
    fun findPendingAcknowledgments(): List<PendingAcknowledgment>

    fun markAckSynced(notificationId: String, syncedAtEpochMillis: Long): Int

    fun markAckError(notificationId: String, errorCategory: String): Int
}
