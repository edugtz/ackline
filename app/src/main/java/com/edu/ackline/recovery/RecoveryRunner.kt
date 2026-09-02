package com.edu.ackline.recovery

import android.util.Log
import com.edu.ackline.push.AlertIngestion
import com.edu.ackline.push.AlertIngestionResult

internal class RecoveryRunner(
    private val remoteClient: RecoveryRemoteClient,
    private val alertIngestion: AlertIngestion,
    private val enqueueAckSync: () -> Unit,
    private val diagnosticLogger: (String) -> Unit = { message -> Log.w(TAG, message) },
) {

    fun run(): RecoveryRunResult =
        when (val remoteResult = remoteClient.fetchPending()) {
            is RecoveryRemoteResult.Success -> reconcile(remoteResult.items)
            RecoveryRemoteResult.NotConfigured -> RecoveryRunResult.NotConfigured
            RecoveryRemoteResult.TransientFailure -> RecoveryRunResult.Retry
            is RecoveryRemoteResult.PermanentFailure -> RecoveryRunResult.PermanentFailure(
                RecoveryErrorCategory.sanitize(remoteResult.category),
            )
        }

    private fun reconcile(items: List<Map<String, String>>): RecoveryRunResult {
        var shouldRetry = false

        items.forEach { item ->
            val ingestionResult = try {
                alertIngestion.ingest(item)
            } catch (_: Exception) {
                shouldRetry = true
                diagnosticLogger("recovery item persistence failed")
                return@forEach
            }

            when (ingestionResult) {
                is AlertIngestionResult.Inserted -> Unit
                AlertIngestionResult.Duplicate -> Unit
                is AlertIngestionResult.Rejected -> {
                    diagnosticLogger(
                        "recovery item rejected: ${ingestionResult.category.diagnosticValue}",
                    )
                }

                AlertIngestionResult.PersistenceFailure -> {
                    shouldRetry = true
                    diagnosticLogger("recovery item persistence failed")
                }
            }
        }

        try {
            enqueueAckSync()
        } catch (_: Exception) {
            shouldRetry = true
            diagnosticLogger("ACK sync scheduling failed after recovery")
        }

        return if (shouldRetry) RecoveryRunResult.Retry else RecoveryRunResult.Success
    }

    private companion object {
        const val TAG = "AcklineRecovery"
    }
}

sealed interface RecoveryRunResult {
    data object Success : RecoveryRunResult

    data object Retry : RecoveryRunResult

    data object NotConfigured : RecoveryRunResult

    data class PermanentFailure(val category: String) : RecoveryRunResult
}
