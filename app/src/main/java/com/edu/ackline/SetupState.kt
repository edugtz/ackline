package com.edu.ackline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RegistrationState { Waiting, Ready, Error }

data class SetupUiState(
    val registrationState: RegistrationState = RegistrationState.Waiting,
    val installationId: String? = null,
    val lastMessageSummary: String? = null,
)

/**
 * Minimal app-process state holder for the Phase 0 setup surface.
 *
 * The push boundary writes into it; the setup screen observes it. No
 * repository/DI architecture: this is deliberately a tiny object.
 *
 * The FID is operational data: it is held in memory for display/copy only and
 * must never be logged.
 */
object SetupState {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun onRegistered(installationId: String) {
        _state.update {
            it.copy(
                registrationState = RegistrationState.Ready,
                installationId = installationId,
            )
        }
    }

    fun onRegistrationError(error: Exception) {
        _state.update { it.copy(registrationState = RegistrationState.Error) }
    }

    fun onMessageReceived(summary: String) {
        _state.update { it.copy(lastMessageSummary = summary) }
    }
}