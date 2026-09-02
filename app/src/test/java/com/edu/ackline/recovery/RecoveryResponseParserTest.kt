package com.edu.ackline.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryResponseParserTest {

    @Test
    fun parsesValidEmptyResponse() {
        val result = RecoveryResponseParser.parseSuccess(
            "{\"ok\":true,\"count\":0,\"items\":[]}",
        )

        assertEquals(RecoveryResponseParseResult.Success(emptyList()), result)
    }

    @Test
    fun parsesMultipleExactEncryptedItems() {
        val result = RecoveryResponseParser.parseSuccess(
            successBody(
                item("nonce-1", "ciphertext-1"),
                item("nonce-2", "ciphertext-2"),
            ),
        )

        assertEquals(
            RecoveryResponseParseResult.Success(
                listOf(
                    mapOf(
                        "v" to "1",
                        "kid" to "ackline-main",
                        "nonce" to "nonce-1",
                        "ciphertext" to "ciphertext-1",
                    ),
                    mapOf(
                        "v" to "1",
                        "kid" to "ackline-main",
                        "nonce" to "nonce-2",
                        "ciphertext" to "ciphertext-2",
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun rejectsCountMismatchAndCountAboveServerBound() {
        assertInvalid("{\"ok\":true,\"count\":1,\"items\":[]}")
        assertInvalid("{\"ok\":true,\"count\":201,\"items\":[]}")
    }

    @Test
    fun rejectsMalformedJsonAndWrongTopLevelShape() {
        assertInvalid("not-json")
        assertInvalid("[]")
        assertInvalid("{\"ok\":true,\"count\":0,\"items\":[],\"extra\":1}")
        assertInvalid("{\"ok\":\"true\",\"count\":0,\"items\":[]}")
    }

    @Test
    fun rejectsItemsWithMissingExtraOrNonStringFields() {
        assertInvalid(
            "{\"ok\":true,\"count\":1,\"items\":[{" +
                "\"v\":\"1\",\"kid\":\"ackline-main\",\"nonce\":\"n\"}]}",
        )
        assertInvalid(
            "{\"ok\":true,\"count\":1,\"items\":[{" +
                "\"v\":\"1\",\"kid\":\"ackline-main\",\"nonce\":\"n\",\"ciphertext\":\"c\",\"extra\":\"x\"}]}",
        )
        assertInvalid(
            "{\"ok\":true,\"count\":1,\"items\":[{" +
                "\"v\":\"1\",\"kid\":42,\"nonce\":\"n\",\"ciphertext\":\"c\"}]}",
        )
    }

    @Test
    fun rejectsNonIntegerOrNegativeCount() {
        assertInvalid("{\"ok\":true,\"count\":1.0,\"items\":[]}")
        assertInvalid("{\"ok\":true,\"count\":-1,\"items\":[]}")
    }

    @Test
    fun recognizesOnlyTheExactTooManyPendingConflict() {
        assertTrue(
            RecoveryResponseParser.isTooManyPendingConflict(
                "{\"ok\":false,\"error\":\"too_many_pending\"}",
            ),
        )
        assertTrue(
            !RecoveryResponseParser.isTooManyPendingConflict(
                "{\"ok\":false,\"error\":\"other\"}",
            ),
        )
    }

    private fun assertInvalid(body: String) {
        assertEquals(RecoveryResponseParseResult.Invalid, RecoveryResponseParser.parseSuccess(body))
    }

    private fun successBody(vararg items: String): String =
        "{\"ok\":true,\"count\":${items.size},\"items\":[${items.joinToString(",")}] }"

    private fun item(nonce: String, ciphertext: String): String =
        "{\"v\":\"1\",\"kid\":\"ackline-main\",\"nonce\":\"$nonce\",\"ciphertext\":\"$ciphertext\"}"
}
