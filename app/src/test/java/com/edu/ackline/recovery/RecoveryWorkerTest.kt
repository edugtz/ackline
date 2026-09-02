package com.edu.ackline.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryWorkerTest {

    @Test
    fun onlyRetryResultMapsToWorkerRetry() {
        assertEquals(
            RecoveryWorkerOutcome.RETRY,
            RecoveryRunResult.Retry.toWorkerOutcome(),
        )
    }

    @Test
    fun terminalResultsMapToWorkerSuccess() {
        listOf<RecoveryRunResult>(
            RecoveryRunResult.Success,
            RecoveryRunResult.NotConfigured,
            RecoveryRunResult.PermanentFailure(RecoveryErrorCategory.HTTP_403),
        ).forEach { result ->
            assertEquals(RecoveryWorkerOutcome.SUCCESS, result.toWorkerOutcome())
        }
    }
}
