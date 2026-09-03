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
import com.edu.ackline.notifications.AcklineNotificationManager
import com.edu.ackline.pairing.FidRePairManager
import com.edu.ackline.pairing.FidRePairStore
import com.edu.ackline.push.AlertIngestion
import com.edu.ackline.recovery.HttpsRecoveryRemoteClient
import com.edu.ackline.recovery.RecoveryRemoteClient
import com.edu.ackline.recovery.RecoveryRunner
import com.edu.ackline.recovery.RecoveryScheduler
import com.edu.ackline.recovery.RecoveryTriggerCoordinator
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

    internal val alertIngestion: AlertIngestion by lazy {
        AlertIngestion(
            decrypt = payloadCrypto::decrypt,
            insertIncoming = alertRepository::insertIncoming,
            notificationPresenter = { envelope ->
                AcklineNotificationManager.show(
                    context = applicationContext,
                    notificationId = envelope.notificationId,
                    level = envelope.level,
                    title = envelope.title,
                    message = envelope.message,
                )
            },
        )
    }

    internal val recoveryRemoteClient: RecoveryRemoteClient by lazy {
        HttpsRecoveryRemoteClient(BuildConfig.ACK_BASE_URL)
    }

    internal val recoveryRunner: RecoveryRunner by lazy {
        RecoveryRunner(
            remoteClient = recoveryRemoteClient,
            alertIngestion = alertIngestion,
            enqueueAckSync = ackSyncScheduler::enqueue,
        )
    }

    internal val recoveryScheduler: RecoveryScheduler by lazy {
        RecoveryScheduler(this)
    }

    internal val recoveryTriggers: RecoveryTriggerCoordinator by lazy {
        RecoveryTriggerCoordinator(
            enqueueOneTime = recoveryScheduler::enqueue,
            onSchedulingFailure = {
                Log.e(TAG, "recovery scheduling failed")
            },
        )
    }

    internal val fidRePairStore: FidRePairStore by lazy {
        FidRePairStore(this)
    }

    internal val fidRePairManager: FidRePairManager by lazy {
        FidRePairManager(
            store = fidRePairStore,
            enqueueRecovery = recoveryTriggers::onFidRegistration,
            publishRestoredState = SetupState::onPairingStateRestored,
            publishObservedState = SetupState::onPairingStateObserved,
            publishUpdatedState = SetupState::onRePairUpdated,
            publishRegistration = SetupState::onRegistered,
            diagnosticLogger = { message -> Log.e(TAG, message) },
        )
    }

    val acknowledgmentExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    private val provisioningExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    override fun onCreate() {
        super.onCreate()
        fidRePairManager.restore()
        try {
            recoveryScheduler.ensurePeriodic()
        } catch (_: Exception) {
            Log.e(TAG, "periodic recovery scheduling failed")
        }
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
            } finally {
                recoveryTriggers.onStartup()
            }
        }
    }

    internal fun markRePairUpdated(): Boolean = fidRePairManager.markRePairUpdated()

    private companion object {
        const val DATABASE_NAME = "ackline.db"
        const val TAG = "AcklineAckSync"
    }
}
