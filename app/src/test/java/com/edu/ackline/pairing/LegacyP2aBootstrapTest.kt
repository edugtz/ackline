package com.edu.ackline.pairing

import com.edu.ackline.RegistrationState
import com.edu.ackline.SetupUiState
import com.edu.ackline.withPairingState
import com.edu.ackline.network.AckBaseUrlProvider
import com.edu.ackline.security.PayloadKeyStore
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class LegacyP2aBootstrapTest {
    private class Storage(var value: FidRePairState = FidRePairState(null, false)) : FidRePairStorage {
        var writes = 0
        var succeeds = true
        override fun read() = value
        override fun write(state: FidRePairState): Boolean {
            writes++
            if (succeeds) value = state
            return succeeds
        }
    }

    @Test fun firstObservationNeverConfirmsButFullProvisioningDoesDurably() {
        val storage = Storage()
        val store = FidRePairStore(storage)
        assertFalse(store.observe("fixture-fid").serverPairingConfirmed)
        val provisioner = PairingProvisioner(
            { _, _ -> PairingClaimResult.Success(PairingClaimResponse("ackline-main", Base64.getEncoder().encodeToString(ByteArray(32)), "https://example.com")) },
            { _, _ -> PayloadKeyStore.ImportResult.IMPORTED },
            { AckBaseUrlProvider.SetResult.STORED },
            { store.confirmServerPairing(it); true },
        )
        assertEquals(PairingProvisioningResult.Success, provisioner.pair("https://example.com/pairing/claim", "s", "t", "fixture-fid"))
        val restarted = FidRePairStore(storage)
        assertTrue(restarted.read().serverPairingConfirmed)
        assertTrue(restarted.read().legacyP2aBootstrapEvaluated)
        val changed = restarted.observe("new-fixture-fid")
        assertTrue(changed.serverPairingConfirmed)
        assertTrue(changed.rePairRequired)
        assertFalse(SetupUiState().withPairingState(changed).needsOnboarding)
        assertEquals(RegistrationState.Waiting, SetupUiState().withPairingState(changed).registrationState)
        assertTrue(restarted.markUpdated().serverPairingConfirmed)
    }

    @Test fun bootstrapWaitsForBothStartupInputsInEitherOrder() {
        for (keyFirst in listOf(true, false)) {
            val bootstrap = LegacyP2aBootstrap(FidRePairState("fixture", false), true)
            assertNull(bootstrap.decision())
            if (keyFirst) bootstrap.provisioningResolved(true, true) else bootstrap.registrationResolved("fixture")
            assertNull(bootstrap.decision())
            if (keyFirst) bootstrap.registrationResolved("fixture") else bootstrap.provisioningResolved(true, true)
            assertEquals(true, bootstrap.decision())
        }
    }

    @Test fun everyEligibilityInputIsRequired() {
        data class Case(val upgrade: Boolean = true, val fid: String? = "fixture", val current: String? = "fixture", val key: Boolean = true, val url: Boolean = true, val repair: Boolean = false)
        val cases = listOf(Case(upgrade=false), Case(fid=null), Case(current=null), Case(current="changed"), Case(key=false), Case(url=false), Case(repair=true), Case(key=false,url=false))
        for (case in cases) {
            val bootstrap = LegacyP2aBootstrap(FidRePairState(case.fid, case.repair), case.upgrade)
            bootstrap.provisioningResolved(case.key, case.url)
            bootstrap.registrationResolved(case.current)
            assertEquals(false, bootstrap.decision())
        }
    }

    @Test fun packageMetadataRequiresPositiveEarlierInstallTime() {
        assertTrue(isPackageUpgrade(100, 200))
        assertFalse(isPackageUpgrade(100, 100))
        assertFalse(isPackageUpgrade(0, 200))
        assertFalse(isPackageUpgrade(200, 100))
    }

    @Test fun decisionsAreAtomicAndIdempotentAfterReload() {
        for (eligible in listOf(true, false)) {
            val storage = Storage(FidRePairState("fixture", false))
            val state = FidRePairStore(storage).evaluateLegacyBootstrap(eligible)
            assertEquals(eligible, state.serverPairingConfirmed)
            assertTrue(state.legacyP2aBootstrapEvaluated)
            val after = FidRePairStore(storage).evaluateLegacyBootstrap(!eligible)
            assertEquals(state, after)
            assertEquals(1, storage.writes)
        }
    }

    @Test fun failedCommitDoesNotConfirmOrFinishAndCanRetryAfterRestart() {
        val storage = Storage(FidRePairState("fixture", false))
        storage.succeeds = false
        assertThrows(IllegalStateException::class.java) { FidRePairStore(storage).evaluateLegacyBootstrap(true) }
        assertFalse(storage.value.serverPairingConfirmed)
        assertFalse(storage.value.legacyP2aBootstrapEvaluated)
        storage.succeeds = true
        assertTrue(FidRePairStore(storage).evaluateLegacyBootstrap(true).serverPairingConfirmed)
    }

    @Test fun managerUsesPreObservationBaselineAndNeverReevaluatesAfterNegativeDecision() {
        val storage = Storage(FidRePairState("old", false))
        var published = storage.value
        val manager = FidRePairManager(FidRePairStore(storage), {}, { published=it }, { published=it }, { published=it }, {}, {}, true)
        manager.restore()
        manager.onRegistered("new")
        manager.onStartupProvisioningResolved(true, true)
        assertFalse(published.serverPairingConfirmed)
        assertTrue(published.legacyP2aBootstrapEvaluated)
        manager.markRePairUpdated()
        manager.onRegistered("old")
        manager.onStartupProvisioningResolved(true, true)
        assertFalse(storage.value.serverPairingConfirmed)
    }

    @Test fun provenUpgradeRecognizedRegardlessOfCallbackOrderAndReload() {
        for (keyFirst in listOf(true, false)) {
            val storage = Storage(FidRePairState("fixture", false))
            val manager = FidRePairManager(FidRePairStore(storage), {}, {}, {}, {}, {}, {}, true)
            manager.restore()
            if (keyFirst) manager.onStartupProvisioningResolved(true, true) else manager.onRegistered("fixture")
            assertFalse(storage.value.legacyP2aBootstrapEvaluated)
            if (keyFirst) manager.onRegistered("fixture") else manager.onStartupProvisioningResolved(true, true)
            assertTrue(FidRePairStore(storage).read().serverPairingConfirmed)
        }
    }

    @Test fun processDeathBeforeKeyResolutionDoesNotEraseMismatchEvidence() {
        val storage = Storage(FidRePairState("old", false))
        fun manager() = FidRePairManager(FidRePairStore(storage), {}, {}, {}, {}, {}, {}, true)
        val first = manager()
        first.restore()
        first.onRegistered("new")
        assertEquals("old", storage.value.lastObservedFid)
        assertFalse(storage.value.legacyP2aBootstrapEvaluated)
        val restarted = manager()
        restarted.restore()
        restarted.onStartupProvisioningResolved(true, true)
        restarted.onRegistered("new")
        assertFalse(storage.value.serverPairingConfirmed)
        assertTrue(storage.value.legacyP2aBootstrapEvaluated)
        assertTrue(storage.value.rePairRequired)
        assertEquals("new", storage.value.lastObservedFid)
    }

    @Test fun failedWriteCannotLeakInMemoryPreferencesConfirmation() {
        val storage = object : FidRePairStorage {
            var value = FidRePairState("fixture", false)
            override fun read() = value
            override fun write(state: FidRePairState): Boolean { value = state; return false }
        }
        val store = FidRePairStore(storage)
        assertThrows(IllegalStateException::class.java) { store.evaluateLegacyBootstrap(true) }
        assertFalse(store.read().serverPairingConfirmed)
        assertFalse(store.read().legacyP2aBootstrapEvaluated)
        assertThrows(IllegalStateException::class.java) { store.evaluateLegacyBootstrap(true) }
    }

    @Test fun registrationFailureIsResolvedUnavailableNotLoading() {
        val bootstrap = LegacyP2aBootstrap(FidRePairState("fixture", false), true)
        bootstrap.provisioningResolved(true, true)
        assertNull(bootstrap.decision())
        bootstrap.registrationResolved(null)
        assertEquals(false, bootstrap.decision())
    }

    @Test fun redactionAndExistingConfirmationPreserved() {
        val storage = Storage(FidRePairState("private-fixture", true, true))
        val result = FidRePairStore(storage).evaluateLegacyBootstrap(false)
        assertTrue(result.serverPairingConfirmed)
        assertFalse(result.toString().contains("private-fixture"))
        assertFalse(SetupUiState(installationId="private-fixture",lastMessageSummary="private-content").toString().contains("private"))
    }
}
