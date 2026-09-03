package com.edu.ackline.pairing

import android.content.Context
import android.content.SharedPreferences

internal data class FidRePairState(
    val lastObservedFid: String?,
    val rePairRequired: Boolean,
)

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

    fun read(): FidRePairState = synchronized(lock) {
        storage.read()
    }

    fun observe(observedFid: String): FidRePairState = synchronized(lock) {
        require(observedFid.isNotBlank())

        val current = storage.read()
        val next = when {
            current.lastObservedFid == null -> FidRePairState(
                lastObservedFid = observedFid,
                rePairRequired = false,
            )

            current.lastObservedFid == observedFid -> current

            else -> FidRePairState(
                lastObservedFid = observedFid,
                rePairRequired = true,
            )
        }

        if (next != current && !storage.write(next)) {
            throw IllegalStateException("FID pairing state could not be persisted")
        }
        next
    }

    fun markUpdated(): FidRePairState = synchronized(lock) {
        val current = storage.read()
        if (!current.rePairRequired) {
            current
        } else {
            val next = current.copy(rePairRequired = false)
            if (!storage.write(next)) {
                throw IllegalStateException("FID pairing state could not be persisted")
            }
            next
        }
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
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "ackline_pairing_state"
        const val KEY_LAST_OBSERVED_FID = "last_observed_fid"
        const val KEY_REPAIR_REQUIRED = "re_pair_required"
    }
}
