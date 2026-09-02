package com.edu.ackline.recovery

import com.edu.ackline.TestPayloads
import com.edu.ackline.data.AlertRepository
import com.edu.ackline.push.AlertIngestion
import com.edu.ackline.push.IncomingAlertEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryRunnerTest {

    @Test
    fun validMissingAlertIsInsertedOnce() {
        var inserts = 0
        val notifications = mutableListOf<IncomingAlertEnvelope>()
        var ackSchedules = 0
        val runner = runner(
            remoteResult = RecoveryRemoteResult.Success(
                listOf(TestPayloads.encryptedData(notificationId = "missing")),
            ),
            insertIncoming = {
                inserts += 1
                AlertRepository.InsertResult.INSERTED
            },
            notifications = notifications,
            enqueueAckSync = { ackSchedules += 1 },
        )

        assertEquals(RecoveryRunResult.Success, runner.run())
        assertEquals(1, inserts)
        assertEquals(1, notifications.size)
        assertEquals(1, ackSchedules)
    }

    @Test
    fun duplicateDoesNotRepostNotificationOrRegressLocalAck() {
        val notifications = mutableListOf<IncomingAlertEnvelope>()
        var ackSchedules = 0
        val runner = runner(
            remoteResult = RecoveryRemoteResult.Success(
                listOf(TestPayloads.encryptedData(notificationId = "acknowledged")),
            ),
            insertIncoming = { AlertRepository.InsertResult.DUPLICATE },
            notifications = notifications,
            enqueueAckSync = { ackSchedules += 1 },
        )

        assertEquals(RecoveryRunResult.Success, runner.run())
        assertTrue(notifications.isEmpty())
        assertEquals(1, ackSchedules)
    }

    @Test
    fun validRejectedValidBatchSkipsOnlyRejectedItem() {
        var inserts = 0
        val notifications = mutableListOf<IncomingAlertEnvelope>()
        val diagnostics = mutableListOf<String>()
        val runner = runner(
            remoteResult = RecoveryRemoteResult.Success(
                listOf(
                    TestPayloads.encryptedData(notificationId = "first"),
                    TestPayloads.encryptedData(kid = "other-kid"),
                    TestPayloads.encryptedData(notificationId = "last"),
                ),
            ),
            insertIncoming = {
                inserts += 1
                AlertRepository.InsertResult.INSERTED
            },
            notifications = notifications,
            diagnostics = diagnostics,
        )

        assertEquals(RecoveryRunResult.Success, runner.run())
        assertEquals(2, inserts)
        assertEquals(2, notifications.size)
        assertEquals(listOf("recovery item rejected: unknown_kid"), diagnostics)
    }

    @Test
    fun persistenceFailureRequestsRetry() {
        var ackSchedules = 0
        val runner = runner(
            remoteResult = RecoveryRemoteResult.Success(
                listOf(TestPayloads.encryptedData()),
            ),
            insertIncoming = { throw IllegalStateException("test-only database failure") },
            enqueueAckSync = { ackSchedules += 1 },
        )

        assertEquals(RecoveryRunResult.Retry, runner.run())
        assertEquals(1, ackSchedules)
    }

    @Test
    fun transientRemoteFailureRequestsRetryWithoutAckDrain() {
        var ackSchedules = 0
        val runner = runner(
            remoteResult = RecoveryRemoteResult.TransientFailure,
            enqueueAckSync = { ackSchedules += 1 },
        )

        assertEquals(RecoveryRunResult.Retry, runner.run())
        assertEquals(0, ackSchedules)
    }

    @Test
    fun permanentRemoteFailureIsTerminalWithoutAckDrain() {
        var ackSchedules = 0
        val runner = runner(
            remoteResult = RecoveryRemoteResult.PermanentFailure("http_403"),
            enqueueAckSync = { ackSchedules += 1 },
        )

        assertEquals(
            RecoveryRunResult.PermanentFailure(RecoveryErrorCategory.HTTP_403),
            runner.run(),
        )
        assertEquals(0, ackSchedules)
    }

    @Test
    fun notConfiguredIsTerminalWithoutAckDrain() {
        var ackSchedules = 0
        val runner = runner(
            remoteResult = RecoveryRemoteResult.NotConfigured,
            enqueueAckSync = { ackSchedules += 1 },
        )

        assertEquals(RecoveryRunResult.NotConfigured, runner.run())
        assertEquals(0, ackSchedules)
    }

    @Test
    fun successfulEmptyResponseStillEnqueuesAckDrainOnce() {
        var ackSchedules = 0
        val runner = runner(
            remoteResult = RecoveryRemoteResult.Success(emptyList()),
            enqueueAckSync = { ackSchedules += 1 },
        )

        assertEquals(RecoveryRunResult.Success, runner.run())
        assertEquals(1, ackSchedules)
    }

    @Test
    fun successfulItemsEnqueueAckDrainOnceNotPerItem() {
        var ackSchedules = 0
        val runner = runner(
            remoteResult = RecoveryRemoteResult.Success(
                listOf(
                    TestPayloads.encryptedData(notificationId = "one"),
                    TestPayloads.encryptedData(notificationId = "two"),
                ),
            ),
            enqueueAckSync = { ackSchedules += 1 },
        )

        assertEquals(RecoveryRunResult.Success, runner.run())
        assertEquals(1, ackSchedules)
    }

    @Test
    fun ackSchedulerIsNotEnqueuedForUnsuccessfulGet() {
        var ackSchedules = 0
        val runner = runner(
            remoteResult = RecoveryRemoteResult.PermanentFailure("http_404"),
            enqueueAckSync = { ackSchedules += 1 },
        )

        runner.run()

        assertEquals(0, ackSchedules)
    }

    @Test
    fun acknowledgedLocalDuplicateIsNotOverwritten() {
        var acknowledgedLocally = true
        var duplicateSeen = false
        val runner = runner(
            remoteResult = RecoveryRemoteResult.Success(
                listOf(TestPayloads.encryptedData(notificationId = "already-acknowledged")),
            ),
            insertIncoming = {
                if (acknowledgedLocally) {
                    duplicateSeen = true
                    AlertRepository.InsertResult.DUPLICATE
                } else {
                    acknowledgedLocally = false
                    AlertRepository.InsertResult.INSERTED
                }
            },
        )

        assertEquals(RecoveryRunResult.Success, runner.run())
        assertTrue(duplicateSeen)
        assertTrue(acknowledgedLocally)
    }

    @Test
    fun ackSchedulerFailureRequestsRetryAfterSuccessfulGet() {
        val runner = runner(
            remoteResult = RecoveryRemoteResult.Success(emptyList()),
            enqueueAckSync = { throw IllegalStateException("test-only scheduler failure") },
        )

        assertEquals(RecoveryRunResult.Retry, runner.run())
    }

    private fun runner(
        remoteResult: RecoveryRemoteResult,
        insertIncoming: (IncomingAlertEnvelope) -> AlertRepository.InsertResult = {
            AlertRepository.InsertResult.INSERTED
        },
        notifications: MutableList<IncomingAlertEnvelope> = mutableListOf(),
        enqueueAckSync: () -> Unit = {},
        diagnostics: MutableList<String> = mutableListOf(),
    ): RecoveryRunner {
        val ingestion = AlertIngestion(
            decrypt = TestPayloads::decrypt,
            insertIncoming = insertIncoming,
            notificationPresenter = {
                notifications += it
                true
            },
        )
        return RecoveryRunner(
            remoteClient = object : RecoveryRemoteClient {
                override fun fetchPending(): RecoveryRemoteResult = remoteResult
            },
            alertIngestion = ingestion,
            enqueueAckSync = enqueueAckSync,
            diagnosticLogger = diagnostics::add,
        )
    }
}
