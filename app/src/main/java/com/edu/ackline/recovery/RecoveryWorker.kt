package com.edu.ackline.recovery

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.edu.ackline.AcklineApplication

class RecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {

    override fun doWork(): Result =
        try {
            val runResult = (applicationContext as AcklineApplication).recoveryRunner.run()
            when (runResult.toWorkerOutcome()) {
                RecoveryWorkerOutcome.RETRY -> Result.retry()
                RecoveryWorkerOutcome.SUCCESS -> {
                    when (runResult) {
                        RecoveryRunResult.NotConfigured -> Log.i(TAG, "recovery not configured")
                        is RecoveryRunResult.PermanentFailure -> {
                            Log.w(
                                TAG,
                                "recovery stopped: " +
                                    RecoveryErrorCategory.sanitize(runResult.category),
                            )
                        }

                        RecoveryRunResult.Success,
                        RecoveryRunResult.Retry,
                        -> Unit
                    }
                    Result.success()
                }
            }
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "recovery failed unexpectedly: ${exception.javaClass.simpleName}",
            )
            Result.retry()
        }

    private companion object {
        const val TAG = "AcklineRecovery"
    }
}

internal enum class RecoveryWorkerOutcome {
    RETRY,
    SUCCESS,
}

internal fun RecoveryRunResult.toWorkerOutcome(): RecoveryWorkerOutcome =
    when (this) {
        RecoveryRunResult.Retry -> RecoveryWorkerOutcome.RETRY
        RecoveryRunResult.Success,
        RecoveryRunResult.NotConfigured,
        is RecoveryRunResult.PermanentFailure,
        -> RecoveryWorkerOutcome.SUCCESS
    }
