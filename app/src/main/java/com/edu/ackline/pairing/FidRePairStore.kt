package com.edu.ackline.pairing

import android.content.Context
import android.content.SharedPreferences

internal data class FidRePairState(
    val lastObservedFid: String?,
    val rePairRequired: Boolean,
    val serverPairingConfirmed: Boolean = false,
    val legacyP2aBootstrapEvaluated: Boolean = false,
) {
    override fun toString(): String = "FidRePairState(<redacted>)"
}

internal interface FidRePairStorage {
    fun read(): FidRePairState

    fun write(state: FidRePairState): Boolean
}

/**
 * Persists and applies the small FID/re-pair state machine.
 *
 * The state transitions are serialized here so registration callbacks and the
 * setup action cannot overwrite one another with a stale snapshot.
 */
internal class FidRePairStore(
    private val storage: FidRePairStorage,
) {

    constructor(context: Context) : this(
        SharedPreferencesFidRePairStorage(context.applicationContext),
    )

    private val lock = Any()
    // SharedPreferences can update its memory even when commit fails. Only successful
    // writes advance this store's process snapshot; never publish an uncommitted flag.
    private var committedState: FidRePairState? = null
    private fun readCommitted(): FidRePairState = committedState ?: storage.read().also { committedState = it }
    private fun writeCommitted(state: FidRePairState): Boolean {
        if (!storage.write(state)) return false
        committedState = state
        return true
    }

    fun read(): FidRePairState = synchronized(lock) {
        readCommitted()
    }

    fun observe(observedFid: String): FidRePairState = synchronized(lock) {
        require(observedFid.isNotBlank())

        val current = readCommitted()
        val next = when {
            current.lastObservedFid == null -> current.copy(
                lastObservedFid = observedFid,
            )

            current.lastObservedFid == observedFid -> current

            else -> current.copy(
                lastObservedFid = observedFid,
                rePairRequired = true,
            )
        }

        if (next != current && !writeCommitted(next)) {
            throw IllegalStateException("FID pairing state could not be persisted")
        }
        next
    }

    /** One-shot compatibility exception, never an inference during normal pairing. */
    fun evaluateLegacyBootstrap(eligible: Boolean): FidRePairState = synchronized(lock) {
        val current = readCommitted()
        if (current.legacyP2aBootstrapEvaluated) return@synchronized current
        val next = current.copy(
            serverPairingConfirmed = current.serverPairingConfirmed || eligible,
            legacyP2aBootstrapEvaluated = true,
        )
        check(writeCommitted(next)) { "Pairing migration could not be persisted" }
        next
    }

    fun confirmServerPairing(currentFid: String): FidRePairState = synchronized(lock) {
        require(currentFid.isNotBlank())

        val current = readCommitted()
        val next = FidRePairState(
            lastObservedFid = currentFid,
            rePairRequired = false,
            serverPairingConfirmed = true,
            legacyP2aBootstrapEvaluated = true,
        )
        if (next != current && !writeCommitted(next)) {
            throw IllegalStateException("FID pairing state could not be persisted")
        }
        next
    }
}

private class SharedPreferencesFidRePairStorage(
    context: Context,
) : FidRePairStorage {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(): FidRePairState = FidRePairState(
        lastObservedFid = preferences.getString(KEY_LAST_OBSERVED_FID, null),
        rePairRequired = preferences.getBoolean(KEY_REPAIR_REQUIRED, false),
        serverPairingConfirmed = preferences.getBoolean(KEY_CONFIRMED, false),
        legacyP2aBootstrapEvaluated = preferences.getBoolean(KEY_BOOTSTRAP, false),
    )

    @Suppress("UseKtx") // The commit result is required to report persistence failure.
    override fun write(state: FidRePairState): Boolean {
        val editor = preferences.edit()
        if (state.lastObservedFid == null) {
            editor.remove(KEY_LAST_OBSERVED_FID)
        } else {
            editor.putString(KEY_LAST_OBSERVED_FID, state.lastObservedFid)
        }
        return editor
            .putBoolean(KEY_REPAIR_REQUIRED, state.rePairRequired)
            .putBoolean(KEY_CONFIRMED, state.serverPairingConfirmed)
            .putBoolean(KEY_BOOTSTRAP, state.legacyP2aBootstrapEvaluated)
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "ackline_pairing_state"
        const val KEY_LAST_OBSERVED_FID = "last_observed_fid"
        const val KEY_CONFIRMED = "server_pairing_confirmed"
        const val KEY_BOOTSTRAP = "legacy_p2a_bootstrap_evaluated"
        const val KEY_REPAIR_REQUIRED = "re_pair_required"
    }
}
