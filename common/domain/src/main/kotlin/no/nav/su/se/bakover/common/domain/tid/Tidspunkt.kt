package no.nav.su.se.bakover.common.tid

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.io.Serializable
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalAdjuster
import java.time.temporal.TemporalAmount
import java.time.temporal.TemporalUnit
import java.util.Date

private val tidspunktPresisjon: ChronoUnit = ChronoUnit.MICROS

/**
 * Wraps Instants and truncates them to microsecond-precision (postgres precision).
 * Purpose is to unify the level of precision such that comparison of time behaves as expected on all levels (code/db).
 * Scenarios to avoid includes cases where timestamps of db-queries wraps around to the next day at different times
 * based on the precision at hand - which may lead to rows not being picked up as expected. This case is especially
 * relevant i.e. when combining timestamp-db-fields (truncated by db) with Instants stored as json (not truncated by db).
 *
 * TODO jah: Bør lage en Json-versjon, domenetyper skal ikke serialiseres/deserialiseres direkte.
 */
class Tidspunkt
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
private constructor(
    @JsonValue
    val instant: Instant,
) : Temporal by instant, TemporalAdjuster by instant, Comparable<Instant> by instant, Serializable by instant {

    init {
        val maxNanoPrecision = when (tidspunktPresisjon) {
            ChronoUnit.MICROS -> 1000
            else -> throw IllegalArgumentException("Unsupported ChronoUnit: $tidspunktPresisjon")
        }
        require(instant.nano % maxNanoPrecision == 0) {
            "Siden postgressql ikke støttet eller støtter nanosekunder, er vår høyeste oppløsning mikrosekunder, forventet instant.nano < 1000000, men var: ${instant.nano}"
        }
    }

    companion object {
        val EPOCH: Tidspunkt get() = Instant.EPOCH.toTidspunkt()
        val MIN: Tidspunkt get() = Instant.MIN.toTidspunkt()
        fun now(clock: Clock) = Tidspunkt(Instant.now(clock).truncatedTo(tidspunktPresisjon))
        fun parse(text: String) = Tidspunkt(Instant.parse(text).truncatedTo(tidspunktPresisjon))
        fun create(instant: Instant) = Tidspunkt(instant.truncatedTo(tidspunktPresisjon))
        fun ofEpochMilli(value: Long): Tidspunkt = create(Instant.ofEpochMilli(value))
    }

    override fun toString() = instant.toString()

    /**
     * Only supports one-way equality check against Instants ("this equals someInstant").
     * Equality check for "someInstant equals this" must be performed by using the wrapped value.
     */
    override fun equals(other: Any?) = when (other) {
        is Tidspunkt -> instant == other.instant
        is Instant -> instant == other.truncatedTo(tidspunktPresisjon)
        else -> false
    }

    override fun compareTo(other: Instant): Int {
        return this.instant.compareTo(other.truncatedTo(tidspunktPresisjon))
    }

    operator fun compareTo(other: Tidspunkt): Int {
        return this.instant.compareTo(other.instant.truncatedTo(tidspunktPresisjon))
    }

    override fun hashCode() = instant.hashCode()
    override fun plus(amount: Long, unit: TemporalUnit): Tidspunkt = instant.plus(amount, unit).toTidspunkt()
    override fun plus(amount: TemporalAmount): Tidspunkt = instant.plus(amount).toTidspunkt()
    override fun minus(amount: Long, unit: TemporalUnit): Tidspunkt = instant.minus(amount, unit).toTidspunkt()
    fun toLocalDate(zoneId: ZoneId): LocalDate = LocalDate.ofInstant(instant, zoneId)
    fun plusUnits(units: Int): Tidspunkt = this.plus(units.toLong(), tidspunktPresisjon)
    val nano = instant.nano

    fun toDate(): Date = Date.from(instant)
}

fun Instant.toTidspunkt() = Tidspunkt.create(this)
fun LocalDateTime.toTidspunkt(zoneId: ZoneId) = this.atZone(zoneId).toTidspunkt()
fun ZonedDateTime.toTidspunkt() = this.toInstant().toTidspunkt()

operator fun Instant.compareTo(tidspunkt: Tidspunkt): Int {
    return this.truncatedTo(tidspunktPresisjon).compareTo(tidspunkt.instant)
}

fun Tidspunkt.fixedClock(): Clock = Clock.fixed(instant, ZoneOffset.UTC)

fun Tidspunkt.between(fraOgMed: Tidspunkt, tilOgMed: Tidspunkt): Boolean {
    return (this == fraOgMed || this == tilOgMed) || this.instant.isAfter(fraOgMed.instant) && this.instant.isBefore(
        tilOgMed.instant,
    )
}
