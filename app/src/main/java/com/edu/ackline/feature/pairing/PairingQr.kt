package com.edu.ackline.feature.pairing

import com.edu.ackline.pairing.isValidClaimValue
import com.edu.ackline.pairing.normalizePairingEndpoint
import org.json.JSONObject
import org.json.JSONTokener

internal data class PairingQr(val endpoint: String, val sessionId: String, val token: String) {
    override fun toString(): String = "PairingQr(<redacted>)"
}

internal sealed interface PairingQrResult {
    data class Valid(val payload: PairingQr) : PairingQrResult
    data object Unrelated : PairingQrResult
    data object Invalid : PairingQrResult
}

internal object PairingQrParser {
    fun parse(input: String): PairingQrResult {
        // Bound work before parsing. Recognizable malformed payloads are invalid, not unrelated.
        val shapedText = input.take(16_384).let {
            it.contains("\"session_id\"") || (it.contains("\"endpoint\"") && it.contains("\"token\""))
        }
        if (input.length > 16_384) return if (shapedText) PairingQrResult.Invalid else PairingQrResult.Unrelated
        val tokener = JSONTokener(input)
        val json = runCatching { tokener.nextValue() as? JSONObject }.getOrNull()
            ?: return if (shapedText) PairingQrResult.Invalid else PairingQrResult.Unrelated
        val shaped = json.has("session_id") || (json.has("endpoint") && json.has("token"))
        if (!shaped) return PairingQrResult.Unrelated
        if (runCatching { tokener.nextClean() != '\u0000' }.getOrDefault(true)) return PairingQrResult.Invalid
        if (json.opt("v") != 1) return PairingQrResult.Invalid
        val endpoint = json.opt("endpoint") as? String ?: return PairingQrResult.Invalid
        val session = json.opt("session_id") as? String ?: return PairingQrResult.Invalid
        val token = json.opt("token") as? String ?: return PairingQrResult.Invalid
        if (!isValidClaimValue(endpoint, 2_048) || normalizePairingEndpoint(endpoint) == null ||
            !isValidClaimValue(session, 256) || !isValidClaimValue(token, 512)
        ) return PairingQrResult.Invalid
        return PairingQrResult.Valid(PairingQr(endpoint.trim(), session, token))
    }
}
