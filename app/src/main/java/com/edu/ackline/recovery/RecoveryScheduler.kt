package com.edu.ackline.recovery

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
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

    fun cancelLegacyPeriodic() {
        try {
            WorkManager.getInstance(appContext)
                .cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "legacy periodic cancellation failed", e)
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ackline-notification-recovery"
        const val LEGACY_PERIODIC_WORK_NAME = "ackline-notification-recovery-periodic"
        const val INITIAL_BACKOFF_SECONDS = 30L
        internal val EXISTING_WORK_POLICY = ExistingWorkPolicy.KEEP

        private const val TAG = "AcklineRecovery"
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
