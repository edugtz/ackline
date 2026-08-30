package com.edu.ackline.ack

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.edu.ackline.AcklineApplication

class AckSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {

    override fun doWork(): Result =
        try {
            val application = applicationContext as AcklineApplication
            if (application.ackSyncRunner.run().shouldRetry) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (_: Exception) {
            Log.e(TAG, "ACK sync failed unexpectedly")
            Result.retry()
        }

    private companion object {
        const val TAG = "AcklineAckSync"
    }
}
