package com.edu.ackline.push

import com.edu.ackline.TestPayloads
import com.edu.ackline.data.AlertRepository
import com.edu.ackline.security.PayloadCrypto
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertIngestionTest {

    @Test
    fun validEncryptedEnvelopeIsInsertedAndPresentedOnce() {
        var insertCalls = 0
        val presentations = mutableListOf<IncomingAlertEnvelope>()
        val ingestion = ingestion(
            insertIncoming = {
                insertCalls += 1
                AlertRepository.InsertResult.INSERTED
            },
            presentations = presentations,
        )

        val result = ingestion.ingest(TestPayloads.encryptedData())

        assertEquals(AlertIngestionResult.Inserted(notificationPosted = true), result)
        assertEquals(1, insertCalls)
        assertEquals(1, presentations.size)
    }

    @Test
    fun duplicateIsReportedWithoutRepostingNotification() {
        val presentations = mutableListOf<IncomingAlertEnvelope>()
        val result = ingestion(
            insertIncoming = { AlertRepository.InsertResult.DUPLICATE },
            presentations = presentations,
        ).ingest(TestPayloads.encryptedData())

        assertEquals(AlertIngestionResult.Duplicate, result)
        assertTrue(presentations.isEmpty())
    }

    @Test
    fun wrongKidIsRejectedWithoutPersistence() {
        var insertCalls = 0
        val result = ingestion(
            insertIncoming = {
                insertCalls += 1
                AlertRepository.InsertResult.INSERTED
            },
        ).ingest(TestPayloads.encryptedData(kid = "other-kid"))

        assertEquals(
            AlertIngestionResult.Rejected(AlertRejectionCategory.UNKNOWN_KID),
            result,
        )
        assertEquals(0, insertCalls)
    }

    @Test
    fun malformedOuterEnvelopeIsRejectedWithoutPersistence() {
        var insertCalls = 0
        val malformed = TestPayloads.encryptedData() - "nonce" + ("extra" to "nope")
        val result = ingestion(
            insertIncoming = {
                insertCalls += 1
                AlertRepository.InsertResult.INSERTED
            },
        ).ingest(malformed)

        assertTrue(result is AlertIngestionResult.Rejected)
        assertEquals(0, insertCalls)
    }

    @Test
    fun decryptAuthenticationFailureIsRejected() {
        val original = TestPayloads.encryptedData()
        val ciphertext = Base64.getUrlDecoder().decode(original.getValue("ciphertext"))
            .also { it[0] = (it[0].toInt() xor 1).toByte() }
        val tampered = original + (
            "ciphertext" to Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext)
        )

        val result = ingestion().ingest(tampered)

        assertEquals(
            AlertIngestionResult.Rejected(AlertRejectionCategory.AUTHENTICATION_FAILED),
            result,
        )
    }

    @Test
    fun keyNotConfiguredIsRejectedWithoutPersistenceOrPresentation() {
        var insertCalls = 0
        val presentations = mutableListOf<IncomingAlertEnvelope>()
        val result = ingestion(
            decrypt = {
                PayloadCrypto.DecryptResult.Rejected(PayloadCrypto.Failure.KEY_NOT_CONFIGURED)
            },
            insertIncoming = {
                insertCalls += 1
                AlertRepository.InsertResult.INSERTED
            },
            presentations = presentations,
        ).ingest(TestPayloads.encryptedData())

        assertEquals(
            AlertIngestionResult.Rejected(AlertRejectionCategory.KEY_NOT_CONFIGURED),
            result,
        )
        assertEquals(0, insertCalls)
        assertTrue(presentations.isEmpty())
    }

    @Test
    fun invalidStrictUtf8IsRejected() {
        val result = ingestion().ingest(
            TestPayloads.encryptedData(plaintext = byteArrayOf(0xC3.toByte(), 0x28)),
        )

        assertEquals(
            AlertIngestionResult.Rejected(AlertRejectionCategory.INVALID_INNER_PAYLOAD),
            result,
        )
    }

    @Test
    fun invalidInnerJsonIsRejected() {
        val result = ingestion().ingest(
            TestPayloads.encryptedData(plaintext = "{".toByteArray(Charsets.UTF_8)),
        )

        assertEquals(
            AlertIngestionResult.Rejected(AlertRejectionCategory.INVALID_INNER_PAYLOAD),
            result,
        )
    }

    @Test
    fun invalidAcklinePayloadIsRejected() {
        val result = ingestion().ingest(
            TestPayloads.encryptedData(
                plaintext = TestPayloads.payload(message = ""),
            ),
        )

        assertEquals(
            AlertIngestionResult.Rejected(AlertRejectionCategory.INVALID_INNER_PAYLOAD),
            result,
        )
    }

    @Test
    fun repositoryFailureIsReportedWithoutFalseInsertedResult() {
        val presentations = mutableListOf<IncomingAlertEnvelope>()
        val result = ingestion(
            insertIncoming = { throw IllegalStateException("test-only database failure") },
            presentations = presentations,
        ).ingest(TestPayloads.encryptedData())

        assertEquals(AlertIngestionResult.PersistenceFailure, result)
        assertTrue(presentations.isEmpty())
    }

    private fun ingestion(
        decrypt: (EncryptedPushEnvelope) -> PayloadCrypto.DecryptResult = TestPayloads::decrypt,
        insertIncoming: (IncomingAlertEnvelope) -> AlertRepository.InsertResult = {
            AlertRepository.InsertResult.INSERTED
        },
        presentations: MutableList<IncomingAlertEnvelope> = mutableListOf(),
    ): AlertIngestion =
        AlertIngestion(
            decrypt = decrypt,
            insertIncoming = insertIncoming,
            notificationPresenter = {
                presentations += it
                true
            },
        )
}
