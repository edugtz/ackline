package com.edu.ackline

import com.edu.ackline.pairing.FidRePairState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RegistrationState { Waiting, Ready, Error }

data class SetupUiState(
    val registrationState: RegistrationState = RegistrationState.Waiting,
    val installationId: String? = null,
    val lastMessageSummary: String? = null,
    val encryptionReady: Boolean = false,
    val rePairRequired: Boolean = false,
    val hasConfirmedPairing: Boolean = false,
    val legacyBootstrapResolved: Boolean = false,
    val ackProvisioned: Boolean = false,
    val notificationGranted: Boolean = false,
) {
    val pairingHealthy: Boolean
        get() = hasConfirmedPairing && encryptionReady && ackProvisioned && !rePairRequired
    val fullyReady: Boolean
        get() = pairingHealthy && registrationState == RegistrationState.Ready &&
            installationId != null && notificationGranted
    val needsOnboarding: Boolean get() = !hasConfirmedPairing
    override fun toString(): String = "SetupUiState(<redacted>)"
}

/**
 * Minimal app-process state holder for the Phase 0 setup surface.
 *
 * The push boundary writes into it; the setup screen observes it. No
 * repository/DI architecture: this is deliberately a tiny object.
 *
 * Durable FID/re-pair state is owned by the app-private pairing store; this
 * mirrors it for UI/process state. The FID is operational data and must never
 * be logged.
 */
object SetupState {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun onNotificationPermissionChanged(granted: Boolean) {
        _state.update { it.copy(notificationGranted = granted) }
    }

    fun onAckProvisioningChanged(provisioned: Boolean) {
        _state.update { it.copy(ackProvisioned = provisioned) }
    }

    fun onRegistered(installationId: String) {
        _state.update {
            it.copy(
                registrationState = RegistrationState.Ready,
                installationId = installationId,
            )
        }
    }

    internal fun onPairingStateRestored(pairingState: FidRePairState) {
        publishPairingState(pairingState)
    }

    internal fun onPairingStateObserved(pairingState: FidRePairState) {
        publishPairingState(pairingState)
    }

    internal fun onRePairUpdated(pairingState: FidRePairState) {
        publishPairingState(pairingState)
    }

    fun onRegistrationError(error: Exception) {
        _state.update { it.copy(registrationState = RegistrationState.Error) }
    }

    fun onMessageReceived(summary: String) {
        _state.update { it.copy(lastMessageSummary = summary) }
    }

    fun onEncryptionStatusChanged(isReady: Boolean) {
        _state.update { it.copy(encryptionReady = isReady) }
    }

    private fun publishPairingState(pairingState: FidRePairState) {
        _state.update { it.withPairingState(pairingState) }
    }
}

internal fun SetupUiState.withPairingState(pairingState: FidRePairState): SetupUiState =
    copy(
        installationId = pairingState.lastObservedFid ?: installationId,
        rePairRequired = pairingState.rePairRequired,
        hasConfirmedPairing = pairingState.serverPairingConfirmed,
        legacyBootstrapResolved = pairingState.legacyP2aBootstrapEvaluated,
    )
