package com.edu.ackline.feature.inbox

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InboxFormattersTest {

    private val zoneId = ZoneId.of("UTC")
    private val now = Instant.parse("2026-09-05T18:10:00Z")

    @Test
    fun relativeTimeUsesTheExplicitReferenceInstant() {
        assertEquals(
            "hace 2 min",
            formatInboxRelativeTime(
                createdAt = now.minusSeconds(2 * 60L),
                now = now,
                zoneId = zoneId,
            ),
        )
        assertEquals(
            "hace 1 h",
            formatInboxRelativeTime(
                createdAt = now.minusSeconds(60 * 60L),
                now = now,
                zoneId = zoneId,
            ),
        )
    }

    @Test
    fun relativeTimeUsesAhoraBelowOneMinute() {
        assertEquals(
            "ahora",
            formatInboxRelativeTime(
                createdAt = now,
                now = now,
                zoneId = zoneId,
            ),
        )
        assertEquals(
            "ahora",
            formatInboxRelativeTime(
                createdAt = now.minusSeconds(59),
                now = now,
                zoneId = zoneId,
            ),
        )
        assertEquals(
            "hace 1 min",
            formatInboxRelativeTime(
                createdAt = now.minusSeconds(60),
                now = now,
                zoneId = zoneId,
            ),
        )
    }

    @Test
    fun relativeTimeNeverEmitsNegativeDurationForFutureTimestamps() {
        val slightlyInFuture = formatInboxRelativeTime(
            createdAt = now.plusSeconds(30),
            now = now,
            zoneId = zoneId,
        )
        assertEquals("5 sep", slightlyInFuture)
        assertFalse(slightlyInFuture.contains("hace -"))

        val skewedFiveMinutes = formatInboxRelativeTime(
            createdAt = now.plusSeconds(5 * 60L),
            now = now,
            zoneId = zoneId,
        )
        assertEquals("5 sep", skewedFiveMinutes)
        assertFalse(skewedFiveMinutes.contains("hace -"))
    }

    @Test
    fun relativeTimeAroundTwentyFourHoursUsesCalendarDay() {
        assertEquals(
            "hace 23 h",
            formatInboxRelativeTime(
                createdAt = now.minusSeconds(23 * 60 * 60L),
                now = now,
                zoneId = zoneId,
            ),
        )
        assertEquals(
            "ayer",
            formatInboxRelativeTime(
                createdAt = now.minusSeconds(24 * 60 * 60L),
                now = now,
                zoneId = zoneId,
            ),
        )
        assertEquals(
            "ayer",
            formatInboxRelativeTime(
                createdAt = now.minusSeconds(24 * 60 * 60L + 1),
                now = now,
                zoneId = zoneId,
            ),
        )
        assertEquals(
            "3 sep",
            formatInboxRelativeTime(
                createdAt = Instant.parse("2026-09-03T18:10:00Z"),
                now = now,
                zoneId = zoneId,
            ),
        )
    }

    @Test
    fun relativeTimeUsesAyerForThePreviousLocalDate() {
        assertEquals(
            "ayer",
            formatInboxRelativeTime(
                createdAt = Instant.parse("2026-09-04T12:00:00Z"),
                now = now,
                zoneId = zoneId,
            ),
        )
    }

    @Test
    fun relativeTimeFallsBackToCompactSpanishDate() {
        assertEquals(
            "5 sep",
            formatInboxRelativeTime(
                createdAt = Instant.parse("2026-09-05T00:00:00Z"),
                now = Instant.parse("2026-09-07T18:10:00Z"),
                zoneId = zoneId,
            ),
        )
        assertEquals(
            "5 sep · 18:10",
            formatInboxDateTime(now, zoneId),
        )
    }

    @Test
    fun tabLabelsUseTheProvidedCurrentCounts() {
        assertEquals("Pendientes (5)", pendingTabLabel(5))
        assertEquals("Vistas (0)", viewedTabLabel(0))
        assertEquals("1 pendiente", pendingCountLabel(1))
    }
}
