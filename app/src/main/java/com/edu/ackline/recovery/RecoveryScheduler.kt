package com.edu.ackline.recovery

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class RecoveryScheduler(context: Context) {

    private val appContext = context.applicationContext

    fun enqueue() {
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            EXISTING_WORK_POLICY,
            buildRecoveryWorkRequest(),
        )
    }

    fun ensurePeriodic() {
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            EXISTING_PERIODIC_WORK_POLICY,
            buildPeriodicRecoveryWorkRequest(),
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ackline-notification-recovery"
        const val UNIQUE_PERIODIC_WORK_NAME = "ackline-notification-recovery-periodic"
        const val INITIAL_BACKOFF_SECONDS = 30L
        const val PERIODIC_INTERVAL_HOURS = 2L
        internal val EXISTING_WORK_POLICY = ExistingWorkPolicy.KEEP
        internal val EXISTING_PERIODIC_WORK_POLICY = ExistingPeriodicWorkPolicy.KEEP
    }
}

internal fun buildRecoveryWorkRequest(): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<RecoveryWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            RecoveryScheduler.INITIAL_BACKOFF_SECONDS,
            TimeUnit.SECONDS,
        )
        .build()

internal fun buildPeriodicRecoveryWorkRequest(): PeriodicWorkRequest =
    PeriodicWorkRequestBuilder<RecoveryWorker>(
        RecoveryScheduler.PERIODIC_INTERVAL_HOURS,
        TimeUnit.HOURS,
    )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            RecoveryScheduler.INITIAL_BACKOFF_SECONDS,
            TimeUnit.SECONDS,
        )
        .build()
