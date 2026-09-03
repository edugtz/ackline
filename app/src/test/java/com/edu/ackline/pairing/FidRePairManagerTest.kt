package com.edu.ackline.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FidRePairManagerTest {

    @Test
    fun firstFidEstablishesBaselineWithoutRePairRequirement() {
        val harness = Harness()

        harness.manager.onRegistered("A")

        assertEquals(FidRePairState("A", false), harness.observedStates.single())
        assertEquals(listOf("A"), harness.registrations)
        assertEquals(1, harness.recoveryEnqueues)
    }

    @Test
    fun sameFidWhileClearRemainsClearAndSchedulesRecovery() {
        val harness = Harness()

        harness.manager.onRegistered("A")
        harness.observedStates.clear()
        harness.recoveryEnqueues = 0
        harness.manager.onRegistered("A")

        assertEquals(FidRePairState("A", false), harness.observedStates.single())
        assertEquals(1, harness.recoveryEnqueues)
    }

    @Test
    fun changedFidRequiresRePairAndSchedulesRecovery() {
        val harness = Harness()

        harness.manager.onRegistered("A")
        harness.manager.onRegistered("B")

        assertEquals(FidRePairState("B", true), harness.observedStates.last())
        assertEquals(2, harness.recoveryEnqueues)
    }

    @Test
    fun sameFidWhileRePairRequiredDoesNotAutoClear() {
        val harness = Harness()

        harness.manager.onRegistered("A")
        harness.manager.onRegistered("B")
        harness.manager.onRegistered("B")

        assertEquals(FidRePairState("B", true), harness.observedStates.last())
        assertEquals(3, harness.recoveryEnqueues)
    }

    @Test
    fun restartRestoresFidAndRePairRequirement() {
        val storage = InMemoryFidRePairStorage()
        val firstManager = harness(storage).manager
        firstManager.onRegistered("A")
        firstManager.onRegistered("B")

        val restarted = harness(storage)
        restarted.manager.restore()

        assertEquals(FidRePairState("B", true), restarted.restoredStates.single())
    }

    @Test
    fun restoreFailureDoesNotPublishStateOrEnqueueRecovery() {
        val storage = InMemoryFidRePairStorage()
        storage.readFailure = IllegalStateException("private test detail")
        val harness = harness(storage)

        harness.manager.restore()

        assertTrue(harness.restoredStates.isEmpty())
        assertEquals(0, harness.recoveryEnqueues)
        assertTrue(harness.diagnostics.contains("FID pairing state restore failed"))
    }

    @Test
    fun markUpdatedPersistsClearAndDoesNotScheduleRecovery() {
        val harness = Harness()
        harness.manager.onRegistered("A")
        harness.manager.onRegistered("B")
        val recoveryEnqueuesBeforeClear = harness.recoveryEnqueues

        assertTrue(harness.manager.markRePairUpdated())

        assertEquals(
            FidRePairState("B", false),
            harness.storage.state,
        )
        assertEquals(FidRePairState("B", false), harness.updatedStates.single())
        assertEquals(recoveryEnqueuesBeforeClear, harness.recoveryEnqueues)
    }

    @Test
    fun restartAfterExplicitClearRestoresClearState() {
        val storage = InMemoryFidRePairStorage()
        val firstManager = harness(storage).manager
        firstManager.onRegistered("A")
        firstManager.onRegistered("B")
        assertTrue(firstManager.markRePairUpdated())

        val restarted = harness(storage)
        restarted.manager.restore()

        assertEquals(FidRePairState("B", false), restarted.restoredStates.single())
    }

    @Test
    fun laterFidChangeRequiresRePairAgainAfterExplicitClear() {
        val harness = Harness()
        harness.manager.onRegistered("A")
        harness.manager.onRegistered("B")
        assertTrue(harness.manager.markRePairUpdated())

        harness.manager.onRegistered("C")

        assertEquals(FidRePairState("C", true), harness.observedStates.last())
        assertEquals(3, harness.recoveryEnqueues)
    }

    @Test
    fun persistenceFailureDoesNotPublishFalseClearAndStillSchedulesRegistrationRecovery() {
        val storage = InMemoryFidRePairStorage()
        val harness = harness(storage)
        harness.manager.onRegistered("A")
        storage.writesSucceed = false

        harness.manager.onRegistered("B")

        assertEquals(FidRePairState("A", false), storage.state)
        assertEquals(FidRePairState("A", false), harness.observedStates.last())
        assertEquals(2, harness.recoveryEnqueues)
        assertTrue(harness.diagnostics.contains("FID pairing state update failed"))
    }

    @Test
    fun failedExplicitClearDoesNotPublishOrReportSuccess() {
        val storage = InMemoryFidRePairStorage(FidRePairState("B", true))
        storage.writesSucceed = false
        val harness = harness(storage)

        assertFalse(harness.manager.markRePairUpdated())

        assertEquals(FidRePairState("B", true), storage.state)
        assertTrue(harness.updatedStates.isEmpty())
    }

    private fun harness(storage: InMemoryFidRePairStorage = InMemoryFidRePairStorage()): Harness =
        Harness(storage)

    private class Harness(
        val storage: InMemoryFidRePairStorage = InMemoryFidRePairStorage(),
    ) {
        var recoveryEnqueues = 0
        val observedStates = mutableListOf<FidRePairState>()
        val restoredStates = mutableListOf<FidRePairState>()
        val updatedStates = mutableListOf<FidRePairState>()
        val registrations = mutableListOf<String>()
        val diagnostics = mutableListOf<String>()
        val manager = FidRePairManager(
            store = FidRePairStore(storage),
            enqueueRecovery = { recoveryEnqueues += 1 },
            publishRestoredState = { restoredStates += it },
            publishObservedState = { observedStates += it },
            publishUpdatedState = { updatedStates += it },
            publishRegistration = { registrations += it },
            diagnosticLogger = { diagnostics += it },
        )
    }

    private class InMemoryFidRePairStorage(
        var state: FidRePairState = FidRePairState(null, false),
    ) : FidRePairStorage {
        var writesSucceed = true
        var readFailure: Exception? = null

        override fun read(): FidRePairState {
            readFailure?.let { throw it }
            return state
        }

        override fun write(state: FidRePairState): Boolean {
            if (!writesSucceed) return false
            this.state = state
            return true
        }
    }
}
