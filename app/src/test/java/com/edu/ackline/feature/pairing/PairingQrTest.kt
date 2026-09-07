package com.edu.ackline.feature.pairing

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PairingQrTest {
    private fun canonical() = JSONObject().put("v",1).put("endpoint","https://example.com/pairing/claim").put("session_id","fixture-session").put("token","fixture-bearer")
    @Test fun validAndUnknownFieldsIgnoredAndRedacted() {
        val input=canonical().put("future", JSONObject().put("unknown",true)).toString()
        val result=PairingQrParser.parse(input) as PairingQrResult.Valid
        assertEquals("fixture-session",result.payload.sessionId)
        for (privateValue in listOf("fixture-session","fixture-bearer","example.com")) assertFalse(result.toString().contains(privateValue))
    }
    @Test fun invalidVersionsAndTypes() {
        for (version in listOf<Any>(0,2,1.5,"1",true,JSONObject.NULL)) assertEquals(PairingQrResult.Invalid,PairingQrParser.parse(canonical().put("v",version).toString()))
        assertEquals(PairingQrResult.Invalid, PairingQrParser.parse(canonical().toString().replace("\"v\":1", "\"v\":1.0")))
        for (field in listOf("v","endpoint","session_id","token")) {
            val json=canonical();json.remove(field)
            assertEquals(PairingQrResult.Invalid,PairingQrParser.parse(json.toString()))
        }
    }
    @Test fun allEndpointRules() {
        for (endpoint in listOf("http://example.com", "https:///path", "https://u@example.com", "https://example.com?q=x", "https://example.com#x", "https://example.com/"+"x".repeat(2048), "\nhttps://example.com", "https://example.com/\u007f")) {
            assertEquals(PairingQrResult.Invalid,PairingQrParser.parse(canonical().put("endpoint",endpoint).toString()))
        }
    }
    @Test fun claimValuesAreBoundedNonblankAndControlFree() {
        for ((field,max) in listOf("session_id" to 256,"token" to 512)) {
            for (value in listOf("", " ", "x".repeat(max+1), "a\nb", "a\u0000b", "a\u0085b")) assertEquals(PairingQrResult.Invalid,PairingQrParser.parse(canonical().put(field,value).toString()))
            assertTrue(PairingQrParser.parse(canonical().put(field,"x".repeat(max)).toString()) is PairingQrResult.Valid)
        }
    }
    @Test fun unrelatedVersusInvalidAndTrailingData() {
        for (input in listOf("hello","https://example.com", "{\"v\":1,\"title\":\"other\"}", "[]")) assertEquals(PairingQrResult.Unrelated,PairingQrParser.parse(input))
        assertEquals(PairingQrResult.Invalid,PairingQrParser.parse("{\"session_id\":"))
        assertEquals(PairingQrResult.Invalid,PairingQrParser.parse(canonical().toString()+"trailing"))
        assertEquals(PairingQrResult.Invalid,PairingQrParser.parse(canonical().toString()+" ".repeat(16384)))
    }
}
