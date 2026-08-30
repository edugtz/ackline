package com.edu.ackline.model

import java.time.Instant

enum class AlertLevel(val wireValue: String) {
    REMEMBER("remember"),
    IMPORTANT("important"),
    URGENT("urgent");

    companion object {
        fun fromWireValue(value: String): AlertLevel? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class Alert(
    val notificationId: String,
    val protocolVersion: Int,
    val level: AlertLevel,
    val title: String,
    val message: String,
    val createdAt: Instant,
    val receivedAt: Instant,
    val acknowledgedAt: Instant?,
    val ackSyncState: AckSyncState,
)
