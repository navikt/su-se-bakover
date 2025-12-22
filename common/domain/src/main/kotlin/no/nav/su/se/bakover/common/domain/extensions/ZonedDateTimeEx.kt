package no.nav.su.se.bakover.common.domain.extensions

import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Date

fun ZonedDateTime.next(atTime: LocalTime): Date {
    return if (this.toLocalTime().isAfter(atTime)) {
        Date.from(
            this.plusDays(1)
                .withHour(atTime.hour)
                .withMinute(atTime.minute)
                .withSecond(atTime.second)
                .toInstant(),
        )
    } else {
        Date.from(
            this.withHour(atTime.hour)
                .withMinute(atTime.minute)
                .withSecond(atTime.second)
                .toInstant(),
        )
    }
}

fun ZonedDateTime.nextFirstDateOfMonth(): Date {
    val candidate = this
        .withDayOfMonth(1)
        .withHour(1)
        .withMinute(1)
        .withSecond(0)
        .withNano(0)

    val next = if (this >= candidate) {
        candidate.plusMonths(1)
    } else {
        candidate
    }

    return Date.from(next.toInstant())
}
