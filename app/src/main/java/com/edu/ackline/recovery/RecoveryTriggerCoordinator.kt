package com.edu.ackline.recovery

/**
 * Coalesces all event-driven one-time recovery signals onto the same scheduler
 * entry point. WorkManager's unique KEEP policy performs the coalescing.
 */
internal class RecoveryTriggerCoordinator(
    private val enqueueOneTime: () -> Unit,
    private val onSchedulingFailure: () -> Unit,
) {

    fun onStartup() = enqueueSafely()

    fun onDeletedMessages() = enqueueSafely()

    fun onFidRegistration() = enqueueSafely()

    private fun enqueueSafely() {
        try {
            enqueueOneTime()
        } catch (_: Exception) {
            runCatching { onSchedulingFailure() }
        }
    }
}
