package com.edu.ackline.pairing

internal class FidRePairManager(
    private val store: FidRePairStore,
    private val enqueueRecovery: () -> Unit,
    private val publishRestoredState: (FidRePairState) -> Unit,
    private val publishObservedState: (FidRePairState) -> Unit,
    private val publishUpdatedState: (FidRePairState) -> Unit,
    private val publishRegistration: (String) -> Unit,
    private val diagnosticLogger: (String) -> Unit,
) {

    private val lock = Any()

    fun restore() = synchronized(lock) {
        try {
            publishRestoredState(store.read())
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
                publishRegistration(installationId)
            } catch (_: Exception) {
                log("FID pairing state update failed")
            }

            try {
                publishObservedState(store.observe(installationId))
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

    fun markRePairUpdated(): Boolean = synchronized(lock) {
        try {
            publishUpdatedState(store.markUpdated())
            true
        } catch (_: Exception) {
            log("FID pairing state update failed")
            false
        }
    }

    private fun log(message: String) {
        runCatching { diagnosticLogger(message) }
    }
}
