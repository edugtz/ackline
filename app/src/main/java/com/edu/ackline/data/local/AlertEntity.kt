package com.edu.ackline.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val notificationId: String,
    val protocolVersion: Int,
    val level: String,
    val title: String,
    val message: String,
    val createdAtEpochMillis: Long,
    val receivedAtEpochMillis: Long,
    val acknowledgedAtEpochMillis: Long?,
)
