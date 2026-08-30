package com.edu.ackline.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcklineMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AcklineDatabase::class.java,
        emptyList(),
    )

    @Test
    fun migrateV1ToV2PreservesExistingAlertAndAddsNoneSyncState() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                "INSERT INTO alerts (" +
                    "notificationId, protocolVersion, level, title, message, " +
                    "createdAtEpochMillis, receivedAtEpochMillis, acknowledgedAtEpochMillis" +
                    ") VALUES (" +
                    "'migration-001', 1, 'important', 'Migrated alert', " +
                    "'Non-sensitive migration test', 1000, 2000, NULL" +
                    ")",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            AcklineDatabase.MIGRATION_1_2,
        ).apply {
            query(
                "SELECT notificationId, protocolVersion, level, title, message, " +
                    "createdAtEpochMillis, receivedAtEpochMillis, " +
                    "acknowledgedAtEpochMillis, ackSyncState " +
                    "FROM alerts WHERE notificationId = 'migration-001'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("migration-001", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals("important", cursor.getString(2))
                assertEquals("Migrated alert", cursor.getString(3))
                assertEquals("Non-sensitive migration test", cursor.getString(4))
                assertEquals(1_000L, cursor.getLong(5))
                assertEquals(2_000L, cursor.getLong(6))
                assertTrue(cursor.isNull(7))
                assertEquals("none", cursor.getString(8))
            }
            close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "ackline-migration-test.db"
    }
}
