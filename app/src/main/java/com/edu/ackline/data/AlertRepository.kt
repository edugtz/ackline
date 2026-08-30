package com.edu.ackline.data

import com.edu.ackline.data.local.AlertDao
import com.edu.ackline.data.local.AlertEntity
import com.edu.ackline.model.Alert
import com.edu.ackline.model.AlertLevel
import com.edu.ackline.push.IncomingAlertEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class AlertRepository(
    private val alertDao: AlertDao,
) {

    enum class InsertResult {
        INSERTED,
        DUPLICATE,
    }

    fun insertIncoming(envelope: IncomingAlertEnvelope): InsertResult {
        val rowId = alertDao.insertIgnore(envelope.toEntity())
        return if (rowId == -1L) InsertResult.DUPLICATE else InsertResult.INSERTED
    }

    fun observePending(): Flow<List<Alert>> =
        alertDao.observePending().map { alerts -> alerts.map(AlertEntity::toAlert) }

    fun observeViewed(): Flow<List<Alert>> =
        alertDao.observeViewed().map { alerts -> alerts.map(AlertEntity::toAlert) }

    fun observeById(notificationId: String): Flow<Alert?> =
        alertDao.observeById(notificationId).map { it?.toAlert() }

    fun findById(notificationId: String): Alert? =
        alertDao.findById(notificationId)?.toAlert()
}

private fun IncomingAlertEnvelope.toEntity(): AlertEntity =
    AlertEntity(
        notificationId = notificationId,
        protocolVersion = protocolVersion,
        level = level.wireValue,
        title = title,
        message = message,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        receivedAtEpochMillis = receivedAt.toEpochMilli(),
        acknowledgedAtEpochMillis = null,
    )

private fun AlertEntity.toAlert(): Alert =
    Alert(
        notificationId = notificationId,
        protocolVersion = protocolVersion,
        level = requireNotNull(AlertLevel.fromWireValue(level)),
        title = title,
        message = message,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        receivedAt = Instant.ofEpochMilli(receivedAtEpochMillis),
        acknowledgedAt = acknowledgedAtEpochMillis?.let(Instant::ofEpochMilli),
    )
