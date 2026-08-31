package com.edu.ackline.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InnerPayloadDecoderTest {

    @Test
    fun decodesOnlyAStringObject() {
        val decoded = InnerPayloadDecoder.decode(
            "{\"protocol\":\"1\",\"title\":\"Non-sensitive\"}".toByteArray(),
        )

        assertEquals("1", decoded?.get("protocol"))
        assertEquals("Non-sensitive", decoded?.get("title"))
    }

    @Test
    fun rejectsMalformedUtf8AndNonStringJsonValues() {
        assertNull(InnerPayloadDecoder.decode(byteArrayOf(0xC3.toByte(), 0x28)))
        listOf(
            "[]",
            "{\"value\":{}}",
            "{\"value\":[]}",
            "{\"value\":1}",
            "{\"value\":true}",
            "{\"value\":null}",
            "not-json",
        ).forEach { value ->
            assertNull(InnerPayloadDecoder.decode(value.toByteArray()))
        }
    }
}
