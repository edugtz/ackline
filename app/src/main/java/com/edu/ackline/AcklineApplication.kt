package com.edu.ackline

import android.app.Application
import androidx.room.Room
import com.edu.ackline.data.AlertRepository
import com.edu.ackline.data.local.AcklineDatabase

class AcklineApplication : Application() {

    val database: AcklineDatabase by lazy {
        Room.databaseBuilder(this, AcklineDatabase::class.java, DATABASE_NAME)
            .build()
    }

    val alertRepository: AlertRepository by lazy {
        AlertRepository(database.alertDao())
    }

    private companion object {
        const val DATABASE_NAME = "ackline.db"
    }
}
