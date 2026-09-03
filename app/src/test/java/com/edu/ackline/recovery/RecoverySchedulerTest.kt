package com.edu.ackline.recovery

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoverySchedulerTest {

    @Test
    fun buildsConnectedExponentialThirtySecondOneTimeWork() {
        val request = buildRecoveryWorkRequest()
        val workSpec = request.workSpec

        assertEquals(RecoveryWorker::class.java.name, workSpec.workerClassName)
        assertEquals(NetworkType.CONNECTED, workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals(
            TimeUnit.SECONDS.toMillis(RecoveryScheduler.INITIAL_BACKOFF_SECONDS),
            workSpec.backoffDelayDuration,
        )
    }

    @Test
    fun buildsConnectedExponentialThirtySecondTwoHourPeriodicWork() {
        val request = buildPeriodicRecoveryWorkRequest()
        val workSpec = request.workSpec

        assertEquals(RecoveryWorker::class.java.name, workSpec.workerClassName)
        assertEquals(NetworkType.CONNECTED, workSpec.constraints.requiredNetworkType)
        assertEquals(
            TimeUnit.HOURS.toMillis(RecoveryScheduler.PERIODIC_INTERVAL_HOURS),
            workSpec.intervalDuration,
        )
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals(
            TimeUnit.SECONDS.toMillis(RecoveryScheduler.INITIAL_BACKOFF_SECONDS),
            workSpec.backoffDelayDuration,
        )
    }

    @Test
    fun usesStableUniqueKeepPolicy() {
        assertEquals("ackline-notification-recovery", RecoveryScheduler.UNIQUE_WORK_NAME)
        assertEquals(ExistingWorkPolicy.KEEP, RecoveryScheduler.EXISTING_WORK_POLICY)
    }

    @Test
    fun usesStableUniquePeriodicKeepPolicy() {
        assertEquals(
            "ackline-notification-recovery-periodic",
            RecoveryScheduler.UNIQUE_PERIODIC_WORK_NAME,
        )
        assertEquals(
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            RecoveryScheduler.EXISTING_PERIODIC_WORK_POLICY,
        )
    }
}
