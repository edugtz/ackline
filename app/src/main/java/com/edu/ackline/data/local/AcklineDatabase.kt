package com.edu.ackline.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AlertEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AcklineDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
}
