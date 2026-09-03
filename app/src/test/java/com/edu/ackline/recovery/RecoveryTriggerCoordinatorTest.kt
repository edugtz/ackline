package com.edu.ackline.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryTriggerCoordinatorTest {

    @Test
    fun startupTriggerEnqueuesOneTimeRecovery() {
        val harness = Harness()

        harness.triggers.onStartup()

        assertEquals(1, harness.enqueues)
    }

    @Test
    fun deletedMessagesTriggerEnqueuesOneTimeRecovery() {
        val harness = Harness()

        harness.triggers.onDeletedMessages()

        assertEquals(1, harness.enqueues)
    }

    @Test
    fun fidRegistrationTriggerEnqueuesOneTimeRecovery() {
        val harness = Harness()

        harness.triggers.onFidRegistration()

        assertEquals(1, harness.enqueues)
    }

    @Test
    fun schedulingFailureIsContainedAndReportedWithoutDetails() {
        val diagnostics = mutableListOf<String>()
        val triggers = RecoveryTriggerCoordinator(
            enqueueOneTime = { throw IllegalStateException("private test detail") },
            onSchedulingFailure = { diagnostics += "recovery scheduling failed" },
        )

        triggers.onDeletedMessages()

        assertEquals(listOf("recovery scheduling failed"), diagnostics)
    }

    private class Harness {
        var enqueues = 0
        val triggers = RecoveryTriggerCoordinator(
            enqueueOneTime = { enqueues += 1 },
            onSchedulingFailure = {},
        )
    }
}
