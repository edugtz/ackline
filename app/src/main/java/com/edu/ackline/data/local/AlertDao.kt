package com.edu.ackline.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
