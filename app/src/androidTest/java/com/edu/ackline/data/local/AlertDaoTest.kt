package com.edu.ackline.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.edu.ackline.ack.PendingAcknowledgment
import com.edu.ackline.model.AckSyncState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertDaoTest {

    private lateinit var database: AcklineDatabase
    private lateinit var dao: AlertDao
    private lateinit var databaseExecutor: ExecutorService
    private val observationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        databaseExecutor = Executors.newSingleThreadExecutor()
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AcklineDatabase::class.java,
        )
            .setQueryExecutor(databaseExecutor)
            .setTransactionExecutor(databaseExecutor)
            .build()
        dao = database.alertDao()
    }

    @After
    fun tearDown() {
        observationScope.cancel()
        database.close()
        databaseExecutor.shutdownNow()
    }

    @Test
    fun insertIgnoreStoresOnePendingRowForDuplicateNotificationId() {
        val alert = alert(
            notificationId = "phase2-dedupe-001",
            createdAtEpochMillis = 2_000L,
        )

        assertTrue(execute { dao.insertIgnore(alert) } > 0L)
        assertEquals(
            -1L,
            execute { dao.insertIgnore(alert.copy(title = "duplicate")) },
        )

        val pending = awaitFirst(dao.observePending())
        assertEquals(listOf(alert), pending)
    }

    @Test
    fun pendingAndViewedQueriesSeparateRowsAndOrderNewestFirst() {
        val older = alert(
            notificationId = "older",
            createdAtEpochMillis = 1_000L,
            receivedAtEpochMillis = 3_000L,
        )
        val newer = alert(
            notificationId = "newer",
            createdAtEpochMillis = 2_000L,
            receivedAtEpochMillis = 2_000L,
        )
        val viewed = alert(
            notificationId = "viewed",
            createdAtEpochMillis = 3_000L,
            receivedAtEpochMillis = 1_000L,
            acknowledgedAtEpochMillis = 4_000L,
        )

        execute {
            dao.insertIgnore(older)
            dao.insertIgnore(newer)
            dao.insertIgnore(viewed)
        }

        assertEquals(listOf(newer, older), awaitFirst(dao.observePending()))
        assertEquals(listOf(viewed), awaitFirst(dao.observeViewed()))
    }

    @Test
    fun firstAcknowledgmentStoresPendingSyncStateAndMovesRowToViewed() {
        val alert = alert(notificationId = "ack-first", createdAtEpochMillis = 1_000L)
        val acknowledgedAt = 4_000L
        execute { dao.insertIgnore(alert) }

        assertEquals(
            1,
            execute { dao.acknowledge(alert.notificationId, acknowledgedAt) },
        )

        val stored = requireNotNull(execute { dao.findById(alert.notificationId) })
        assertEquals(acknowledgedAt, stored.acknowledgedAtEpochMillis)
        assertEquals(AckSyncState.PENDING.storageValue, stored.ackSyncState)
        assertEquals(emptyList<AlertEntity>(), awaitFirst(dao.observePending()))
        assertEquals(listOf(stored), awaitFirst(dao.observeViewed()))
    }

    @Test
    fun repeatedAcknowledgmentDoesNotOverwriteOriginalTimestamp() {
        val alert = alert(notificationId = "ack-repeat", createdAtEpochMillis = 1_000L)
        execute {
            dao.insertIgnore(alert)
            dao.acknowledge(alert.notificationId, 4_000L)
        }

        assertEquals(
            0,
            execute { dao.acknowledge(alert.notificationId, 9_000L) },
        )

        val stored = requireNotNull(execute { dao.findById(alert.notificationId) })
        assertEquals(4_000L, stored.acknowledgedAtEpochMillis)
        assertEquals(AckSyncState.PENDING.storageValue, stored.ackSyncState)
    }

    @Test
    fun acknowledgmentOfUnknownIdDoesNotCreateRow() {
        assertEquals(0, execute { dao.acknowledge("unknown", 4_000L) })
        assertEquals(null, execute { dao.findById("unknown") })
    }

    @Test
    fun duplicateInsertAfterAcknowledgmentPreservesAcknowledgedRow() {
        val alert = alert(notificationId = "ack-duplicate", createdAtEpochMillis = 1_000L)
        execute {
            dao.insertIgnore(alert)
            dao.acknowledge(alert.notificationId, 4_000L)
        }

        assertEquals(
            -1L,
            execute { dao.insertIgnore(alert.copy(title = "duplicate payload")) },
        )

        val stored = requireNotNull(execute { dao.findById(alert.notificationId) })
        assertEquals(alert.title, stored.title)
        assertEquals(4_000L, stored.acknowledgedAtEpochMillis)
        assertEquals(AckSyncState.PENDING.storageValue, stored.ackSyncState)
    }

    @Test
    fun pendingAcknowledgmentProjectionOnlyIncludesAcknowledgedPendingRows() {
        val pending = alert(
            notificationId = "sync-pending",
            createdAtEpochMillis = 1_000L,
            acknowledgedAtEpochMillis = 4_000L,
            ackToken = "test-token",
        )
        val none = alert(notificationId = "sync-none", createdAtEpochMillis = 2_000L)
        val synced = alert(
            notificationId = "sync-synced",
            createdAtEpochMillis = 3_000L,
            acknowledgedAtEpochMillis = 5_000L,
            ackSyncState = AckSyncState.SYNCED.storageValue,
            ackSyncedAtEpochMillis = 6_000L,
        )
        val error = alert(
            notificationId = "sync-error",
            createdAtEpochMillis = 4_000L,
            acknowledgedAtEpochMillis = 6_000L,
            ackSyncState = AckSyncState.ERROR.storageValue,
            lastAckError = "http_403",
        )

        execute {
            dao.insertIgnore(pending)
            dao.insertIgnore(none)
            dao.insertIgnore(synced)
            dao.insertIgnore(error)
        }

        assertEquals(
            listOf(PendingAcknowledgment("sync-pending", 4_000L, "test-token")),
            execute { dao.findPendingAcknowledgments() },
        )
    }

    @Test
    fun markAckSyncedSetsSyncMetadataClearsErrorAndPreservesLocalAck() {
        val alert = alert(
            notificationId = "sync-success",
            createdAtEpochMillis = 1_000L,
            acknowledgedAtEpochMillis = 4_000L,
            lastAckError = "http_403",
            ackToken = "test-token",
        )
        execute { dao.insertIgnore(alert) }

        assertEquals(
            1,
            execute { dao.markAckSynced(alert.notificationId, 7_000L) },
        )

        val stored = requireNotNull(execute { dao.findById(alert.notificationId) })
        assertEquals(4_000L, stored.acknowledgedAtEpochMillis)
        assertEquals(AckSyncState.SYNCED.storageValue, stored.ackSyncState)
        assertEquals(7_000L, stored.ackSyncedAtEpochMillis)
        assertEquals(null, stored.lastAckError)
        assertEquals(alert.ackToken, stored.ackToken)
    }

    @Test
    fun markAckErrorSetsTerminalMetadataAndDoesNotModifyNonPendingRows() {
        val pending = alert(
            notificationId = "sync-error-pending",
            createdAtEpochMillis = 1_000L,
            acknowledgedAtEpochMillis = 4_000L,
            ackToken = "test-token",
        )
        val synced = alert(
            notificationId = "sync-error-synced",
            createdAtEpochMillis = 2_000L,
            acknowledgedAtEpochMillis = 5_000L,
            ackSyncState = AckSyncState.SYNCED.storageValue,
            ackSyncedAtEpochMillis = 6_000L,
        )
        execute {
            dao.insertIgnore(pending)
            dao.insertIgnore(synced)
        }

        assertEquals(
            1,
            execute {
                dao.markAckError(pending.notificationId, "http_404")
            },
        )
        assertEquals(
            0,
            execute {
                dao.markAckError(synced.notificationId, "http_403")
            },
        )

        val storedPending = requireNotNull(execute { dao.findById(pending.notificationId) })
        assertEquals(4_000L, storedPending.acknowledgedAtEpochMillis)
        assertEquals(AckSyncState.ERROR.storageValue, storedPending.ackSyncState)
        assertEquals("http_404", storedPending.lastAckError)

        val storedSynced = requireNotNull(execute { dao.findById(synced.notificationId) })
        assertEquals(synced, storedSynced)
    }

    private fun alert(
        notificationId: String,
        createdAtEpochMillis: Long,
        receivedAtEpochMillis: Long = createdAtEpochMillis,
        acknowledgedAtEpochMillis: Long? = null,
        ackSyncState: String = if (acknowledgedAtEpochMillis == null) {
            AckSyncState.NONE.storageValue
        } else {
            AckSyncState.PENDING.storageValue
        },
        ackSyncedAtEpochMillis: Long? = null,
        lastAckError: String? = null,
        ackToken: String? = null,
    ) = AlertEntity(
        notificationId = notificationId,
        protocolVersion = 1,
        level = "important",
        title = "Test alert $notificationId",
        message = "Non-sensitive test message",
        createdAtEpochMillis = createdAtEpochMillis,
        receivedAtEpochMillis = receivedAtEpochMillis,
        acknowledgedAtEpochMillis = acknowledgedAtEpochMillis,
        ackSyncState = ackSyncState,
        ackSyncedAtEpochMillis = ackSyncedAtEpochMillis,
        lastAckError = lastAckError,
        ackToken = ackToken,
    )

    private fun <T> execute(operation: () -> T): T =
        databaseExecutor.submit<T> { operation() }.get(5, TimeUnit.SECONDS)

    private fun <T> awaitFirst(flow: Flow<T>): T {
        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        val job = observationScope.launch {
            try {
                flow.first {
                    result.set(it)
                    latch.countDown()
                    true
                }
            } catch (throwable: Throwable) {
                failure.set(throwable)
                latch.countDown()
            }
        }

        try {
            assertTrue("Room flow did not emit", latch.await(5, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Room flow failed", it) }
            return result.get()
        } finally {
            job.cancel()
        }
    }
}
