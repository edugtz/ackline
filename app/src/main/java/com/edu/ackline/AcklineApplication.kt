package com.edu.ackline

import android.app.Application
import androidx.room.Room
import com.edu.ackline.ack.LocalAcknowledgmentManager
import com.edu.ackline.data.AlertRepository
import com.edu.ackline.data.local.AcklineDatabase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AcklineApplication : Application() {

    val database: AcklineDatabase by lazy {
        Room.databaseBuilder(this, AcklineDatabase::class.java, DATABASE_NAME)
            .addMigrations(AcklineDatabase.MIGRATION_1_2)
            .build()
    }

    val alertRepository: AlertRepository by lazy {
        AlertRepository(database.alertDao())
    }

    val localAcknowledgmentManager: LocalAcknowledgmentManager by lazy {
        LocalAcknowledgmentManager(this, alertRepository)
    }

    val acknowledgmentExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    private companion object {
        const val DATABASE_NAME = "ackline.db"
    }
}
