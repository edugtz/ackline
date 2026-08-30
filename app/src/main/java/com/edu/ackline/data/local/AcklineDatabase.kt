package com.edu.ackline.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlertEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AcklineDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE alerts ADD COLUMN ackSyncState TEXT NOT NULL DEFAULT 'none'",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE alerts ADD COLUMN ackSyncedAtEpochMillis INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE alerts ADD COLUMN lastAckError TEXT",
                )
                db.execSQL(
                    "ALTER TABLE alerts ADD COLUMN ackToken TEXT",
                )
            }
        }
    }
}
