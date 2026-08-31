package com.edu.ackline

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.edu.ackline.ack.AckRemoteClient
import com.edu.ackline.ack.AckSyncRunner
import com.edu.ackline.ack.AckSyncScheduler
import com.edu.ackline.ack.HttpsAckRemoteClient
import com.edu.ackline.ack.LocalAcknowledgmentManager
import com.edu.ackline.data.AlertRepository
import com.edu.ackline.data.local.AcklineDatabase
import com.edu.ackline.security.PayloadCrypto
import com.edu.ackline.security.PayloadKeyStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AcklineApplication : Application() {

    val database: AcklineDatabase by lazy {
        Room.databaseBuilder(this, AcklineDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                AcklineDatabase.MIGRATION_1_2,
                AcklineDatabase.MIGRATION_2_3,
            )
            .build()
    }

    val alertRepository: AlertRepository by lazy {
        AlertRepository(database.alertDao())
    }

    val localAcknowledgmentManager: LocalAcknowledgmentManager by lazy {
        LocalAcknowledgmentManager(this, alertRepository, ackSyncScheduler)
    }

    val ackSyncScheduler: AckSyncScheduler by lazy {
        AckSyncScheduler(this)
    }

    val ackRemoteClient: AckRemoteClient by lazy {
        HttpsAckRemoteClient(BuildConfig.ACK_BASE_URL)
    }

    val ackSyncRunner: AckSyncRunner by lazy {
        AckSyncRunner(alertRepository, ackRemoteClient)
    }

    internal val payloadKeyStore: PayloadKeyStore by lazy {
        PayloadKeyStore(this)
    }

    internal val payloadCrypto: PayloadCrypto by lazy {
        PayloadCrypto(payloadKeyStore)
    }

    val acknowledgmentExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    private val provisioningExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    override fun onCreate() {
        super.onCreate()
        try {
            ackSyncScheduler.enqueue()
        } catch (_: Exception) {
            Log.e(TAG, "startup ACK sync scheduling failed")
        }
        provisioningExecutor.execute {
            try {
                payloadKeyStore.importStagedKeyIfPresent(BuildConfig.PAYLOAD_ENCRYPTION_KID)
                SetupState.onEncryptionStatusChanged(
                    payloadKeyStore.isReady(BuildConfig.PAYLOAD_ENCRYPTION_KID),
                )
            } catch (_: Exception) {
                Log.e(TAG, "payload key provisioning failed")
                SetupState.onEncryptionStatusChanged(false)
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "ackline.db"
        const val TAG = "AcklineAckSync"
    }
}
