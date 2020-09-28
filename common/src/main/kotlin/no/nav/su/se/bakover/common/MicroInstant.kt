package no.nav.su.se.bakover.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.io.Serializable
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalAdjuster

abstract class TruncatedInstant(
    @JsonValue
    val instant: Instant
) : Temporal by instant,
    TemporalAdjuster by instant,
    Comparable<Instant> by instant,
    Serializable by instant {

    val nano = instant.nano
    fun toEpochMilli() = instant.toEpochMilli()
    override fun toString() = instant.toString()
}

class MicroInstant @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    instant: Instant
) : TruncatedInstant(instant.truncatedTo(unit)) {

    companion object {
        val unit: ChronoUnit = ChronoUnit.MICROS
        val EPOCH: MicroInstant get() = Instant.EPOCH.toMicroInstant()
        val MIN: MicroInstant get() = Instant.MIN.toMicroInstant()
        fun now(clock: Clock = Clock.systemUTC()) = MicroInstant(Instant.now(clock))
    }

    /**
     * Only supports one-way equality check against Instants ("this equals someInstant").
     * Equality check for "someInstant equals this" must be performed by using the wrapped value.
     */
    override fun equals(other: Any?) = when (other) {
        is MicroInstant -> instant == other.instant
        is Instant -> instant == other.truncatedTo(unit)
        else -> false
    }

    override fun hashCode() = instant.hashCode()
    fun plusSeconds(secondsToAdd: Long) = instant.plusSeconds(secondsToAdd).toMicroInstant()
}

fun Instant.toMicroInstant() = MicroInstant(this)
fun LocalDateTime.toMicroInstant(zoneOffset: ZoneOffset = ZoneOffset.UTC) = this.toInstant(zoneOffset).toMicroInstant()
fun ZonedDateTime.toMicroInstant() = this.toInstant().toMicroInstant()
