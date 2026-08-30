package com.edu.ackline.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.edu.ackline.ack.PendingAcknowledgment
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore(alert: AlertEntity): Long

    @Query(
        "SELECT * FROM alerts " +
            "WHERE acknowledgedAtEpochMillis IS NULL " +
            "ORDER BY createdAtEpochMillis DESC, receivedAtEpochMillis DESC",
    )
    fun observePending(): Flow<List<AlertEntity>>

    @Query(
        "SELECT * FROM alerts " +
            "WHERE acknowledgedAtEpochMillis IS NOT NULL " +
            "ORDER BY createdAtEpochMillis DESC, receivedAtEpochMillis DESC",
    )
    fun observeViewed(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE notificationId = :notificationId")
    fun observeById(notificationId: String): Flow<AlertEntity?>

    @Query("SELECT * FROM alerts WHERE notificationId = :notificationId")
    fun findById(notificationId: String): AlertEntity?

    @Query(
        "UPDATE alerts " +
            "SET acknowledgedAtEpochMillis = :acknowledgedAtEpochMillis, " +
            "    ackSyncState = 'pending' " +
            "WHERE notificationId = :notificationId " +
            "  AND acknowledgedAtEpochMillis IS NULL",
    )
    fun acknowledge(
        notificationId: String,
        acknowledgedAtEpochMillis: Long,
    ): Int

    @Query(
        "SELECT notificationId, acknowledgedAtEpochMillis, ackToken " +
            "FROM alerts " +
            "WHERE acknowledgedAtEpochMillis IS NOT NULL " +
            "  AND ackSyncState = 'pending' " +
            "ORDER BY acknowledgedAtEpochMillis ASC",
    )
    fun findPendingAcknowledgments(): List<PendingAcknowledgment>

    @Query(
        "UPDATE alerts " +
            "SET ackSyncState = 'synced', " +
            "    ackSyncedAtEpochMillis = :syncedAtEpochMillis, " +
            "    lastAckError = NULL " +
            "WHERE notificationId = :notificationId " +
            "  AND acknowledgedAtEpochMillis IS NOT NULL " +
            "  AND ackSyncState = 'pending'",
    )
    fun markAckSynced(
        notificationId: String,
        syncedAtEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE alerts " +
            "SET ackSyncState = 'error', " +
            "    ackSyncedAtEpochMillis = NULL, " +
            "    lastAckError = :errorCategory " +
            "WHERE notificationId = :notificationId " +
            "  AND acknowledgedAtEpochMillis IS NOT NULL " +
            "  AND ackSyncState = 'pending'",
    )
    fun markAckError(
        notificationId: String,
        errorCategory: String,
    ): Int
}
