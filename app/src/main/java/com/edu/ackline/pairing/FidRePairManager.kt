package com.edu.ackline.pairing

internal class FidRePairManager(
    private val store: FidRePairStore,
    private val enqueueRecovery: () -> Unit,
    private val publishRestoredState: (FidRePairState) -> Unit,
    private val publishObservedState: (FidRePairState) -> Unit,
    private val publishUpdatedState: (FidRePairState) -> Unit,
    private val publishRegistration: (String) -> Unit,
    private val diagnosticLogger: (String) -> Unit,
    private val packageUpgrade: Boolean? = null,
) {

    private val lock = Any()

    private var bootstrap: LegacyP2aBootstrap? = null
    private var pendingObservation: String? = null

    fun restore() = synchronized(lock) {
        try {
            val state = store.read()
            if (packageUpgrade != null && bootstrap == null) {
                bootstrap = LegacyP2aBootstrap(state, packageUpgrade)
            }
            publishRestoredState(state)
            finishBootstrapIfResolved()
        } catch (_: Exception) {
            log("FID pairing state restore failed")
        }
    }

    fun onRegistered(installationId: String) {
        if (installationId.isBlank()) {
            log("FID pairing state update failed")
            return
        }

        synchronized(lock) {
            try {
                if (bootstrap != null) {
                    // Preserve the old durable baseline across process death while keys load.
                    pendingObservation = installationId
                    bootstrap?.registrationResolved(installationId)
                    finishBootstrapIfResolved()
                } else {
                    publishObservedState(store.observe(installationId))
                }
                publishRegistration(installationId)
            } catch (_: Exception) {
                log("FID pairing state update failed")
            } finally {
                try {
                    enqueueRecovery()
                } catch (_: Exception) {
                    log("recovery scheduling failed")
                }
            }
        }
    }

    fun onStartupProvisioningResolved(encryptionReady: Boolean, ackProvisioned: Boolean) = synchronized(lock) {
        bootstrap?.provisioningResolved(encryptionReady, ackProvisioned)
        finishBootstrapIfResolved()
    }

    fun onRegistrationFailed() = synchronized(lock) {
        bootstrap?.registrationResolved(null)
        finishBootstrapIfResolved()
    }

    private fun finishBootstrapIfResolved() {
        val eligible = bootstrap?.decision() ?: return
        try {
            val decided = store.evaluateLegacyBootstrap(eligible)
            val observed = pendingObservation?.let(store::observe) ?: decided
            publishUpdatedState(observed)
            pendingObservation = null
            bootstrap = null
        } catch (_: Exception) {
            // No onboarding claim is allowed until the durable decision is saved.
            log("Pairing migration persistence failed")
        }
    }

    fun markRePairUpdated(): Boolean = synchronized(lock) {
        try {
            publishUpdatedState(store.markUpdated())
            true
        } catch (_: Exception) {
            log("FID pairing state update failed")
            false
        }
    }

    fun markServerPairingConfirmed(currentFid: String): Boolean = synchronized(lock) {
        if (currentFid.isBlank()) {
            log("FID pairing confirmation failed")
            return@synchronized false
        }

        try {
            publishUpdatedState(store.confirmServerPairing(currentFid))
            true
        } catch (_: Exception) {
            log("FID pairing confirmation failed")
            false
        }
    }

    private fun log(message: String) {
        runCatching { diagnosticLogger(message) }
    }
}
