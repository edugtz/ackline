package com.edu.ackline.feature.inbox

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private val spanishMonthAbbreviations = listOf(
    "",
    "ene",
    "feb",
    "mar",
    "abr",
    "may",
    "jun",
    "jul",
    "ago",
    "sep",
    "oct",
    "nov",
    "dic",
)

internal fun formatInboxRelativeTime(
    createdAt: Instant,
    now: Instant,
    zoneId: ZoneId,
): String {
    val elapsed = Duration.between(createdAt, now)
    if (!elapsed.isNegative) {
        when {
            elapsed < Duration.ofMinutes(1) -> return "ahora"
            elapsed < Duration.ofHours(1) -> {
                return "hace ${elapsed.toMinutes()} min"
            }
            elapsed < Duration.ofHours(24) -> {
                return "hace ${elapsed.toHours()} h"
            }
        }

        val createdDate = createdAt.atZone(zoneId).toLocalDate()
        val nowDate = now.atZone(zoneId).toLocalDate()
        if (createdDate.plusDays(1) == nowDate) {
            return "ayer"
        }
    }

    return formatInboxDate(createdAt, zoneId)
}

internal fun formatInboxDateTime(
    instant: Instant,
    zoneId: ZoneId,
): String {
    val localDateTime = instant.atZone(zoneId).toLocalDateTime()
    return buildString {
        append(localDateTime.dayOfMonth)
        append(' ')
        append(spanishMonthAbbreviations[localDateTime.monthValue])
        append(" · ")
        append(localDateTime.hour.toString().padStart(2, '0'))
        append(':')
        append(localDateTime.minute.toString().padStart(2, '0'))
    }
}

private fun formatInboxDate(
    instant: Instant,
    zoneId: ZoneId,
): String {
    val localDate = instant.atZone(zoneId).toLocalDate()
    return "${localDate.dayOfMonth} ${spanishMonthAbbreviations[localDate.monthValue]}"
}
