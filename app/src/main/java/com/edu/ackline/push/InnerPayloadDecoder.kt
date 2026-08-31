package com.edu.ackline.push

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import org.json.JSONObject

internal object InnerPayloadDecoder {

    fun decode(plaintext: ByteArray): Map<String, String>? {
        val json = decodeUtf8Strictly(plaintext) ?: return null
        val objectValue = runCatching { JSONObject(json) }.getOrNull() ?: return null

        val result = linkedMapOf<String, String>()
        val keys = objectValue.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = objectValue.opt(key)
            if (value !is String) return null
            result[key] = value
        }
        return result
    }

    private fun decodeUtf8Strictly(plaintext: ByteArray): String? =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(plaintext))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
}
