package com.edu.ackline.recovery

import org.json.JSONArray
import org.json.JSONObject

internal sealed interface RecoveryResponseParseResult {
    data class Success(val items: List<Map<String, String>>) : RecoveryResponseParseResult

    data object Invalid : RecoveryResponseParseResult
}

internal object RecoveryResponseParser {
    private const val MAX_PENDING_ITEMS = 200
    private val SUCCESS_KEYS = setOf("ok", "count", "items")
    private val CONFLICT_KEYS = setOf("ok", "error")
    private val ENVELOPE_KEYS = setOf("v", "kid", "nonce", "ciphertext")

    fun parseSuccess(body: String): RecoveryResponseParseResult {
        val response = runCatching { JSONObject(body) }.getOrNull()
            ?: return RecoveryResponseParseResult.Invalid

        if (response.keysSet() != SUCCESS_KEYS || response.opt("ok") != true) {
            return RecoveryResponseParseResult.Invalid
        }

        val count = response.opt("count").asBoundedCount() ?:
            return RecoveryResponseParseResult.Invalid
        val items = response.opt("items") as? JSONArray
            ?: return RecoveryResponseParseResult.Invalid
        if (count != items.length()) {
            return RecoveryResponseParseResult.Invalid
        }

        val parsedItems = ArrayList<Map<String, String>>(count)
        for (index in 0 until items.length()) {
            val item = items.opt(index) as? JSONObject
                ?: return RecoveryResponseParseResult.Invalid
            if (item.keysSet() != ENVELOPE_KEYS) {
                return RecoveryResponseParseResult.Invalid
            }

            val envelope = linkedMapOf<String, String>()
            for (key in ENVELOPE_KEYS) {
                val value = item.opt(key) as? String
                    ?: return RecoveryResponseParseResult.Invalid
                envelope[key] = value
            }
            parsedItems += envelope
        }

        return RecoveryResponseParseResult.Success(parsedItems)
    }

    fun isTooManyPendingConflict(body: String): Boolean {
        val response = runCatching { JSONObject(body) }.getOrNull() ?: return false
        return response.keysSet() == CONFLICT_KEYS &&
            response.opt("ok") == false &&
            response.opt("error") == RecoveryErrorCategory.TOO_MANY_PENDING
    }

    private fun JSONObject.keysSet(): Set<String> {
        val keys = mutableSetOf<String>()
        val iterator = keys()
        while (iterator.hasNext()) {
            keys += iterator.next()
        }
        return keys
    }

    private fun Any?.asBoundedCount(): Int? =
        when (this) {
            is Int -> takeIf { it in 0..MAX_PENDING_ITEMS }
            is Long -> takeIf { it in 0..MAX_PENDING_ITEMS }?.toInt()
            else -> null
        }
}
