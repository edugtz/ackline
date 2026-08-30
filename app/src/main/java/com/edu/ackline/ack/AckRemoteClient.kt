package com.edu.ackline.ack

sealed interface AckRemoteResult {
    data object Success : AckRemoteResult
    data object NotConfigured : AckRemoteResult
    data object TransientFailure : AckRemoteResult
    data class PermanentFailure(val category: String) : AckRemoteResult
}

interface AckRemoteClient {
    fun acknowledge(
        notificationId: String,
        ackToken: String,
    ): AckRemoteResult
}

object AckErrorCategory {
    const val MISSING_ACK_TOKEN = "missing_ack_token"
    const val HTTP_400 = "http_400"
    const val HTTP_403 = "http_403"
    const val HTTP_404 = "http_404"
    const val HTTP_3XX = "http_3xx"
    const val CLIENT_ERROR = "client_error"

    fun sanitize(category: String): String =
        when (category) {
            MISSING_ACK_TOKEN,
            HTTP_400,
            HTTP_403,
            HTTP_404,
            HTTP_3XX,
            CLIENT_ERROR -> category

            else -> CLIENT_ERROR
        }
}
