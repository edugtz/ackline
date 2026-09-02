package com.edu.ackline.recovery

sealed interface RecoveryRemoteResult {
    data class Success(val items: List<Map<String, String>>) : RecoveryRemoteResult

    data object NotConfigured : RecoveryRemoteResult

    data object TransientFailure : RecoveryRemoteResult

    data class PermanentFailure(val category: String) : RecoveryRemoteResult
}

interface RecoveryRemoteClient {
    fun fetchPending(): RecoveryRemoteResult
}

object RecoveryErrorCategory {
    const val CONFIGURATION_ERROR = "configuration_error"
    const val CONTRACT_ERROR = "contract_error"
    const val TOO_MANY_PENDING = "too_many_pending"
    const val HTTP_403 = "http_403"
    const val HTTP_404 = "http_404"
    const val HTTP_3XX = "http_3xx"
    const val CLIENT_ERROR = "client_error"

    fun sanitize(category: String): String =
        when (category) {
            CONFIGURATION_ERROR,
            CONTRACT_ERROR,
            TOO_MANY_PENDING,
            HTTP_403,
            HTTP_404,
            HTTP_3XX,
            CLIENT_ERROR -> category

            else -> CLIENT_ERROR
        }
}
