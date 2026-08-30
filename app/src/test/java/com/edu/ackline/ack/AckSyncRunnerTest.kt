package com.edu.ackline.ack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AckSyncRunnerTest {

    @Test
    fun successMarksPendingAcknowledgmentSynced() {
        val store = FakeAckSyncStore(
            PendingAcknowledgment("success", 1_000L, "token"),
        )
        val runner = AckSyncRunner(
            store = store,
            remoteClient = FakeAckRemoteClient(mapOf("success" to AckRemoteResult.Success)),
            syncedAtEpochMillis = { 2_000L },
        )

        val result = runner.run()

        assertFalse(result.shouldRetry)
        assertEquals(mapOf("success" to 2_000L), store.synced)
        assertEquals(emptyMap<String, String>(), store.errors)
        assertEquals(1_000L, store.originalAcknowledgedAt("success"))
    }

    @Test
    fun transientFailureLeavesRowPendingAndRequestsRetry() {
        val store = FakeAckSyncStore(
            PendingAcknowledgment("transient", 1_000L, "token"),
        )
        val runner = AckSyncRunner(
            store = store,
            remoteClient = FakeAckRemoteClient(
                mapOf("transient" to AckRemoteResult.TransientFailure),
            ),
        )

        val result = runner.run()

        assertTrue(result.shouldRetry)
        assertEquals(emptyMap<String, Long>(), store.synced)
        assertEquals(emptyMap<String, String>(), store.errors)
        assertEquals(1_000L, store.originalAcknowledgedAt("transient"))
    }

    @Test
    fun notConfiguredLeavesPendingRowUnchangedWithoutRetry() {
        val store = FakeAckSyncStore(
            PendingAcknowledgment("not-configured", 1_000L, "token"),
        )
        val runner = AckSyncRunner(
            store = store,
            remoteClient = FakeAckRemoteClient(
                mapOf("not-configured" to AckRemoteResult.NotConfigured),
            ),
        )

        val result = runner.run()

        assertFalse(result.shouldRetry)
        assertTrue(store.synced.isEmpty())
        assertTrue(store.errors.isEmpty())
        assertTrue(store.ackSyncedAtEpochMillis.isEmpty())
        assertTrue(store.lastAckError.isEmpty())
        assertEquals(1_000L, store.originalAcknowledgedAt("not-configured"))
    }

    @Test
    fun permanentFailureMarksErrorWithoutRequestingRetryForThatRow() {
        val store = FakeAckSyncStore(
            PendingAcknowledgment("permanent", 1_000L, "token"),
        )
        val runner = AckSyncRunner(
            store = store,
            remoteClient = FakeAckRemoteClient(
                mapOf(
                    "permanent" to AckRemoteResult.PermanentFailure("http_404"),
                ),
            ),
        )

        val result = runner.run()

        assertFalse(result.shouldRetry)
        assertEquals(mapOf("permanent" to "http_404"), store.errors)
        assertEquals(1_000L, store.originalAcknowledgedAt("permanent"))
    }

    @Test
    fun missingTokenBecomesSanitizedTerminalError() {
        val store = FakeAckSyncStore(
            PendingAcknowledgment("missing-token", 1_000L, " "),
        )
        val runner = AckSyncRunner(
            store = store,
            remoteClient = FakeAckRemoteClient(emptyMap()),
        )

        val result = runner.run()

        assertFalse(result.shouldRetry)
        assertEquals(
            mapOf("missing-token" to AckErrorCategory.MISSING_ACK_TOKEN),
            store.errors,
        )
    }

    @Test
    fun mixedRowsProgressIndependentlyAndOnlyTransientRowsRequestRetry() {
        val store = FakeAckSyncStore(
            PendingAcknowledgment("success", 1_000L, "success-token"),
            PendingAcknowledgment("transient", 2_000L, "transient-token"),
            PendingAcknowledgment("permanent", 3_000L, "permanent-token"),
        )
        val runner = AckSyncRunner(
            store = store,
            remoteClient = FakeAckRemoteClient(
                mapOf(
                    "success" to AckRemoteResult.Success,
                    "transient" to AckRemoteResult.TransientFailure,
                    "permanent" to AckRemoteResult.PermanentFailure("unexpected"),
                ),
            ),
            syncedAtEpochMillis = { 4_000L },
        )

        val result = runner.run()

        assertTrue(result.shouldRetry)
        assertEquals(mapOf("success" to 4_000L), store.synced)
        assertEquals(mapOf("permanent" to AckErrorCategory.CLIENT_ERROR), store.errors)
        assertEquals(1_000L, store.originalAcknowledgedAt("success"))
        assertEquals(2_000L, store.originalAcknowledgedAt("transient"))
        assertEquals(3_000L, store.originalAcknowledgedAt("permanent"))
    }

    private class FakeAckRemoteClient(
        private val results: Map<String, AckRemoteResult>,
    ) : AckRemoteClient {
        override fun acknowledge(notificationId: String, ackToken: String): AckRemoteResult =
            results.getValue(notificationId)
    }

    private class FakeAckSyncStore(vararg rows: PendingAcknowledgment) : AckSyncStore {
        private val pendingRows = rows.toList()
        val synced = linkedMapOf<String, Long>()
        val errors = linkedMapOf<String, String>()
        val ackSyncedAtEpochMillis = linkedMapOf<String, Long>()
        val lastAckError = linkedMapOf<String, String>()

        override fun findPendingAcknowledgments(): List<PendingAcknowledgment> = pendingRows

        override fun markAckSynced(notificationId: String, syncedAtEpochMillis: Long): Int {
            ackSyncedAtEpochMillis[notificationId] = syncedAtEpochMillis
            synced[notificationId] = syncedAtEpochMillis
            return 1
        }

        override fun markAckError(notificationId: String, errorCategory: String): Int {
            lastAckError[notificationId] = errorCategory
            errors[notificationId] = errorCategory
            return 1
        }

        fun originalAcknowledgedAt(notificationId: String): Long =
            pendingRows.first { it.notificationId == notificationId }.acknowledgedAtEpochMillis
    }
}
