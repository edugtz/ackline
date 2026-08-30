package com.edu.ackline.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    private fun alert(
        notificationId: String,
        createdAtEpochMillis: Long,
        receivedAtEpochMillis: Long = createdAtEpochMillis,
        acknowledgedAtEpochMillis: Long? = null,
    ) = AlertEntity(
        notificationId = notificationId,
        protocolVersion = 1,
        level = "important",
        title = "Test alert $notificationId",
        message = "Non-sensitive test message",
        createdAtEpochMillis = createdAtEpochMillis,
        receivedAtEpochMillis = receivedAtEpochMillis,
        acknowledgedAtEpochMillis = acknowledgedAtEpochMillis,
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
