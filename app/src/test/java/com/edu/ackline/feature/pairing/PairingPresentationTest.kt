package com.edu.ackline.feature.pairing

import com.edu.ackline.RegistrationState
import com.edu.ackline.SetupUiState
import com.edu.ackline.feature.onboarding.OnboardingCompletion
import com.edu.ackline.feature.onboarding.onboardingCompletion
import com.edu.ackline.pairing.PairingClaimFailure
import com.edu.ackline.pairing.PairingProvisioningFailure
import com.edu.ackline.pairing.PairingProvisioningResult
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class PairingPresentationTest {
    private val qr = """{"v":1,"endpoint":"https://example.com/pairing/claim","session_id":"fixture","token":"bearer"}"""
    private val ready = SetupUiState(registrationState=RegistrationState.Ready, installationId="fixture", legacyBootstrapResolved=true)

    @Test fun everyClaimFailureHasConservativeActionAndProductCopy() {
        val expected = mapOf(
            PairingClaimFailure.TRANSPORT to PairingErrorAction.RetryOnce,
            PairingClaimFailure.TAILSCALE_IDENTITY_REQUIRED to PairingErrorAction.RetrySameQr,
            PairingClaimFailure.RATE_LIMITED to PairingErrorAction.RetrySameQr,
            PairingClaimFailure.REPLACE_REQUIRED to PairingErrorAction.ReplacementQr,
            PairingClaimFailure.SERVER_MISCONFIGURED to PairingErrorAction.Operator,
        )
        for (failure in PairingClaimFailure.entries) {
            val error = pairingError(failure)
            assertEquals(expected[failure] ?: PairingErrorAction.NewQr, error.action)
            assertTrue(error.message.isNotBlank())
            for (word in listOf("HTTP", "FID", "token", "session", "--replace", "/pairing", "Firebase")) assertFalse(error.message.contains(word))
        }
    }
    @Test fun everyLocalFailureRequiresNewQr() {
        val failures = listOf(PairingProvisioningFailure.InvalidInput, PairingProvisioningFailure.InvalidResponse, PairingProvisioningFailure.InvalidKeyEncoding, PairingProvisioningFailure.InvalidKeySize, PairingProvisioningFailure.KeyImportFailed, PairingProvisioningFailure.AckBaseUrlInvalid, PairingProvisioningFailure.AckBaseUrlPersistenceFailed, PairingProvisioningFailure.FidConfirmationFailed)
        for (failure in failures) assertEquals(PairingErrorAction.NewQr,pairingError(failure).action)
        for (failure in PairingClaimFailure.entries) assertEquals(pairingError(failure),pairingError(PairingProvisioningFailure.ClaimFailed(failure)))
    }
    @Test fun prerequisiteTransitionsAndProductionScannerInput() {
        var setup=SetupUiState()
        var vpn=false
        var claims=0
        val presenter=PairingPresenter(Executor { it.run() },{setup},{vpn}) { _, _ -> claims++;PairingProvisioningResult.Success }
        assertEquals(PairingPresentation.Idle,presenter.state.value)
        presenter.refresh()
        assertEquals(PairingPresentation.WaitingForRegistration,presenter.state.value)
        presenter.acceptScannedQr(qr)
        assertEquals(0,claims)
        setup=ready
        presenter.refresh()
        assertEquals(PairingPresentation.TailscaleRequired,presenter.state.value)
        vpn=true;presenter.refresh()
        assertEquals(PairingPresentation.ReadyToScan,presenter.state.value)
        presenter.beginScan()
        presenter.acceptScannedQr("unrelated")
        assertEquals(PairingPresentation.Scanning,presenter.state.value)
        presenter.acceptScannedQr(qr)
        assertEquals(1,claims)
        assertEquals(PairingPresentation.Success,presenter.state.value)
    }
    @Test fun claimIsEnqueuedAndCannotBeDoubleSubmittedAndDenialNeverListo() {
        var work: Runnable?=null
        var claims=0
        var setup=ready
        val presenter=PairingPresenter(Executor { work=it },{setup},{true}) { _, _ ->
            claims++
            setup=setup.copy(hasConfirmedPairing=true,encryptionReady=true,ackProvisioned=true)
            PairingProvisioningResult.Success
        }
        presenter.refresh();presenter.beginScan();presenter.acceptScannedQr(qr)
        assertEquals(0,claims)
        assertEquals(PairingPresentation.Pairing,presenter.state.value)
        presenter.acceptScannedQr(qr)
        work!!.run()
        assertEquals(1,claims)
        assertEquals(PairingPresentation.Success,presenter.state.value)
        assertEquals(OnboardingCompletion.Incomplete,onboardingCompletion(setup,true))
        assertFalse(setup.needsOnboarding)
        assertEquals(OnboardingCompletion.Ready,onboardingCompletion(setup.copy(notificationGranted=true),true))
    }
    @Test fun ambiguousTransportOneRetryThenConsumedRequiresNewQr() {
        val results=ArrayDeque(listOf(PairingClaimFailure.TRANSPORT,PairingClaimFailure.CONSUMED))
        val presenter=PairingPresenter(Executor { it.run() },{ready},{true}) { _, _ -> PairingProvisioningResult.Failure(PairingProvisioningFailure.ClaimFailed(results.removeFirst())) }
        presenter.refresh();presenter.beginScan();presenter.acceptScannedQr(qr)
        assertEquals(PairingErrorAction.RetryOnce,(presenter.state.value as PairingPresentation.Error).error.action)
        presenter.retryScan();presenter.beginScan();presenter.acceptScannedQr(qr)
        assertEquals(PairingErrorAction.NewQr,(presenter.state.value as PairingPresentation.Error).error.action)
    }
    @Test fun repeatedAmbiguousTransportStopsOfferingSameQr() {
        val presenter=PairingPresenter(Executor { it.run() },{ready},{true}) { _, _ -> PairingProvisioningResult.Failure(PairingProvisioningFailure.ClaimFailed(PairingClaimFailure.TRANSPORT)) }
        presenter.refresh();presenter.beginScan();presenter.acceptScannedQr(qr)
        presenter.retryScan();presenter.beginScan();presenter.acceptScannedQr(qr)
        assertEquals(PairingErrorAction.NewQr,(presenter.state.value as PairingPresentation.Error).error.action)
    }
    @Test fun unexpectedExceptionRequiresNewQrAndMigrationGatePreventsClaim() {
        var setup=ready.copy(legacyBootstrapResolved=false)
        var calls=0
        val presenter=PairingPresenter(Executor { it.run() },{setup},{true}) { _, _ -> calls++;throw IllegalStateException("private fixture") }
        presenter.refresh();presenter.beginScan();presenter.acceptScannedQr(qr)
        assertEquals(0,calls)
        setup=ready;presenter.refresh();presenter.beginScan();presenter.acceptScannedQr(qr)
        assertEquals(PairingErrorAction.NewQr,(presenter.state.value as PairingPresentation.Error).error.action)
        assertFalse(presenter.state.value.toString().contains("private fixture"))
    }
    @Test fun closingAndReopeningScannerCannotResetAmbiguousSessionBudget() {
        var claims = 0
        val presenter = PairingPresenter(Executor { it.run() }, { ready }, { true }) { _, _ ->
            claims++
            PairingProvisioningResult.Failure(PairingProvisioningFailure.ClaimFailed(PairingClaimFailure.TRANSPORT))
        }
        repeat(3) {
            presenter.finishFlow(); presenter.refresh(); presenter.beginScan()
            presenter.cancelScan(); presenter.beginScan()
            presenter.acceptScannedQr(qr)
        }
        assertEquals(2, claims)
        assertEquals(PairingErrorAction.NewQr, (presenter.state.value as PairingPresentation.Error).error.action)
        presenter.retryScan(); presenter.beginScan()
        presenter.acceptScannedQr(qr.replace("fixture", "new-session"))
        assertEquals(3, claims)
        assertEquals(PairingErrorAction.RetryOnce, (presenter.state.value as PairingPresentation.Error).error.action)
    }

    @Test fun refreshKeepsActiveScanAndCancelRejectsLateFrame() {
        var claims = 0
        val presenter = PairingPresenter(Executor { it.run() }, { ready }, { true }) { _, _ ->
            claims++; PairingProvisioningResult.Success
        }
        presenter.refresh(); presenter.beginScan(); presenter.refresh()
        assertEquals(PairingPresentation.Scanning, presenter.state.value)
        presenter.cancelScan(); presenter.acceptScannedQr(qr)
        assertEquals(0, claims)
        assertEquals(PairingPresentation.ReadyToScan, presenter.state.value)
    }

}
