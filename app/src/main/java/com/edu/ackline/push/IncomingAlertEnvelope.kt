package com.edu.ackline.push

import com.edu.ackline.model.AlertLevel
import java.time.Instant

data class IncomingAlertEnvelope(
    val protocolVersion: Int,
    val notificationId: String,
    val level: AlertLevel,
    val title: String,
    val message: String,
    val createdAt: Instant,
    val receivedAt: Instant,
    val ackToken: String? = null,
)

internal fun parseAcklinePayload(data: Map<String, String>): IncomingAlertEnvelope? {
    if (data["protocol"] != PROTOCOL_VERSION) return null

    val notificationId = data["notification_id"] ?: return null
    val levelValue = data["level"] ?: return null
    val title = data["title"] ?: return null
    val message = data["message"] ?: return null
    val createdAtValue = data["created_at"] ?: return null

    if (notificationId.isBlank() || title.isBlank() || message.isBlank() || createdAtValue.isBlank()) {
        return null
    }

    val level = AlertLevel.fromWireValue(levelValue) ?: return null
    val createdAt = runCatching { Instant.parse(createdAtValue) }.getOrNull() ?: return null
    val ackToken = data["ack_token"]?.takeIf { it.isNotBlank() }

    return IncomingAlertEnvelope(
        protocolVersion = PROTOCOL_VERSION.toInt(),
        notificationId = notificationId,
        level = level,
        title = title,
        message = message,
        createdAt = createdAt,
        receivedAt = Instant.now(),
        ackToken = ackToken,
    )
}

private const val PROTOCOL_VERSION = "1"
