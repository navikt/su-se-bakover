package no.nav.su.se.bakover.common.domain.backoff

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun Failures.shouldRetry(
    clock: Clock,
    delayTable: Map<Long, Duration> = mapOf(
        1L to 1.minutes,
        2L to 5.minutes,
        3L to 15.minutes,
        4L to 30.minutes,
        5L to 1.hours,
        6L to 2.hours,
        7L to 4.hours,
        8L to 8.hours,
        9L to 12.hours,
        10L to 24.hours,
    ),
    maxDelay: Duration = 24.hours,
): Either<Unit, Failures> {
    if (last == null) return this.inc(clock).right()
    val delayDuration = delayTable[count]?.coerceAtMost(maxDelay) ?: maxDelay
    val nextRetryTime = last.plus(delayDuration)
    return if (Tidspunkt.now(clock) >= nextRetryTime) {
        this.inc(clock).right()
    } else {
        Unit.left()
    }
}
