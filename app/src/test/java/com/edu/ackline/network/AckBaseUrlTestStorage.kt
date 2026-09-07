package com.edu.ackline.network

internal class InMemoryAckBaseUrlStorage(
    private var value: String? = null,
) : AckBaseUrlStorage {

    var writesSucceed: Boolean = true
    var clearsSucceed: Boolean = true

    override fun read(): String? = value

    override fun write(baseUrl: String): Boolean {
        if (!writesSucceed) return false
        value = baseUrl
        return true
    }

    override fun clear(): Boolean {
        if (!clearsSucceed) return false
        value = null
        return true
    }
}
