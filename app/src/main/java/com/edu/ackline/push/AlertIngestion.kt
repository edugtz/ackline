package com.edu.ackline.push

import android.util.Log
import com.edu.ackline.BuildConfig
import com.edu.ackline.data.AlertRepository
import com.edu.ackline.security.PayloadCrypto

/**
 * Canonical boundary for encrypted alert ingestion.
 *
 * Firebase and recovery both provide the exact same encrypted envelope shape.
 * This component owns validation, decryption, payload validation, idempotent
 * persistence, and notification presentation for both callers.
 */
internal class AlertIngestion(
    private val decrypt: (EncryptedPushEnvelope) -> PayloadCrypto.DecryptResult,
    private val insertIncoming: (IncomingAlertEnvelope) -> AlertRepository.InsertResult,
    private val notificationPresenter: (IncomingAlertEnvelope) -> Boolean,
) {

    fun ingest(data: Map<String, String>): AlertIngestionResult {
        val encryptedEnvelope = when (val parsed = EncryptedPushEnvelope.parse(data)) {
            is EncryptedPushEnvelope.ParseResult.Success -> parsed.envelope
            is EncryptedPushEnvelope.ParseResult.Rejected -> {
                return AlertIngestionResult.Rejected(parsed.failure.toDiagnosticCategory())
            }
        }

        if (encryptedEnvelope.kid != BuildConfig.PAYLOAD_ENCRYPTION_KID) {
            return AlertIngestionResult.Rejected(AlertRejectionCategory.UNKNOWN_KID)
        }

        val plaintext = when (val decrypted = decrypt(encryptedEnvelope)) {
            is PayloadCrypto.DecryptResult.Success -> decrypted.plaintext
            is PayloadCrypto.DecryptResult.Rejected -> {
                return AlertIngestionResult.Rejected(decrypted.failure.toDiagnosticCategory())
            }
        }

        val innerData = InnerPayloadDecoder.decode(plaintext)
            ?: return AlertIngestionResult.Rejected(AlertRejectionCategory.INVALID_INNER_PAYLOAD)
        val envelope = parseAcklinePayload(innerData)
            ?: return AlertIngestionResult.Rejected(AlertRejectionCategory.INVALID_INNER_PAYLOAD)

        val insertResult = try {
            insertIncoming(envelope)
        } catch (_: Exception) {
            return AlertIngestionResult.PersistenceFailure
        }

        return when (insertResult) {
            AlertRepository.InsertResult.INSERTED -> {
                val notificationPosted = try {
                    notificationPresenter(envelope)
                } catch (_: Exception) {
                    Log.w(TAG, "notification presentation failed")
                    false
                }
                AlertIngestionResult.Inserted(notificationPosted)
            }

            AlertRepository.InsertResult.DUPLICATE -> AlertIngestionResult.Duplicate
        }
    }

    private companion object {
        const val TAG = "AcklineIngestion"
    }
}

internal sealed interface AlertIngestionResult {
    data class Inserted(val notificationPosted: Boolean) : AlertIngestionResult

    data object Duplicate : AlertIngestionResult

    data class Rejected(val category: AlertRejectionCategory) : AlertIngestionResult

    data object PersistenceFailure : AlertIngestionResult
}

internal enum class AlertRejectionCategory(val diagnosticValue: String) {
    MALFORMED_ENVELOPE("malformed_envelope"),
    UNSUPPORTED_VERSION("unsupported_version"),
    OVERSIZE("oversize"),
    UNKNOWN_KID("unknown_kid"),
    KEY_NOT_CONFIGURED("key_not_configured"),
    AUTHENTICATION_FAILED("authentication_failed"),
    INVALID_INNER_PAYLOAD("invalid_inner_payload"),
}

private fun EncryptedPushEnvelope.Failure.toDiagnosticCategory(): AlertRejectionCategory =
    when (this) {
        EncryptedPushEnvelope.Failure.MALFORMED_ENVELOPE ->
            AlertRejectionCategory.MALFORMED_ENVELOPE

        EncryptedPushEnvelope.Failure.UNSUPPORTED_VERSION ->
            AlertRejectionCategory.UNSUPPORTED_VERSION

        EncryptedPushEnvelope.Failure.OVERSIZE -> AlertRejectionCategory.OVERSIZE
    }

private fun PayloadCrypto.Failure.toDiagnosticCategory(): AlertRejectionCategory =
    when (this) {
        PayloadCrypto.Failure.KEY_NOT_CONFIGURED -> AlertRejectionCategory.KEY_NOT_CONFIGURED
        PayloadCrypto.Failure.AUTHENTICATION_FAILED ->
            AlertRejectionCategory.AUTHENTICATION_FAILED
    }
