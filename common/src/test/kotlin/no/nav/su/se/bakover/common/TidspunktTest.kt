package no.nav.su.se.bakover.common

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class TidspunktTest {
    @Test
    fun `truncate instant to same format as repo, precision in micros`() {
        val instant = Instant.now()
        val tidspunkt = instant.toTidspunkt()
        instant.toEpochMilli() - tidspunkt.toEpochMilli() shouldBe 0
        instant.toString() shouldHaveLength tidspunkt.toString().length

        val addedMicros = instant.plus(251, ChronoUnit.MICROS)
        val microAddedMicros = addedMicros.toTidspunkt()
        addedMicros shouldBe microAddedMicros.instant
        microAddedMicros shouldBe addedMicros

        val addedNanos = instant.plusNanos(378)
        val microAddedNanos = addedNanos.toTidspunkt()
        addedNanos.nano - microAddedNanos.nano shouldBeGreaterThan 0
        addedNanos shouldNotBe microAddedNanos.instant
        microAddedNanos shouldBe addedNanos
    }

    @Test
    fun `should equal instant truncated to same precision`() {
        val instant = Instant.now().plusNanos(515)
        val tidspunkt = instant.toTidspunkt()
        instant shouldNotBe tidspunkt.instant
        instant.truncatedTo(Tidspunkt.unit) shouldBe tidspunkt.instant
        tidspunkt shouldBe instant
        tidspunkt shouldBe instant.truncatedTo(Tidspunkt.unit)
        val othertidspunkt = instant.toTidspunkt()
        tidspunkt shouldBe othertidspunkt
        tidspunkt shouldBe tidspunkt
    }

    @Test
    fun `comparison`() {
        val startOfDayInstant = 1.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC)
        val endOfDayInstant = 1.januar(2020).plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC)
        val startOfDaytidspunkt = startOfDayInstant.toTidspunkt()
        val endOfDaytidspunkt = endOfDayInstant.toTidspunkt()

        startOfDayInstant shouldBeLessThan endOfDayInstant
        startOfDaytidspunkt.instant shouldBeLessThan endOfDaytidspunkt.instant
        startOfDayInstant shouldBeLessThan endOfDaytidspunkt.instant
        endOfDayInstant shouldBeGreaterThan startOfDaytidspunkt.instant
    }

    @Test
    fun `new instances for companions`() {
        val firstEpoch = Tidspunkt.EPOCH
        val secondEpoch = Tidspunkt.EPOCH
        firstEpoch shouldNotBeSameInstanceAs secondEpoch
        val firstMin = Tidspunkt.MIN
        val secondMin = Tidspunkt.MIN
        firstMin shouldNotBeSameInstanceAs secondMin
    }

    @Test
    fun `should serialize as instant`() {
        val start = 1.oktober(2020).endOfDay()
        val serialized = objectMapper.writeValueAsString(start)
        val ske = objectMapper.writeValueAsString(Instant.now())
        serialized shouldBe "\"2020-10-01T23:59:59.999999Z\"" // TODO Spennende at denne serialiseres til double qoutes?
        val deserialized = objectMapper.readValue(serialized, Tidspunkt::class.java)
        deserialized shouldBe start

        val objSerialized = objectMapper.writeValueAsString(NestedSerialization(start))
        objSerialized shouldBe """{"tidspunkt":"2020-10-01T23:59:59.999999Z","other":"other values"}"""
        val objDeserialized = objectMapper.readValue(objSerialized, NestedSerialization::class.java)
        objDeserialized shouldBe NestedSerialization(start, "other values")
    }

    @Test
    fun `now`() {
        val fixed = Clock.fixed(1.januar(2020).endOfDay().instant, ZoneOffset.UTC)
        val now = Tidspunkt.now(fixed)
        now.toString() shouldBe "2020-01-01T23:59:59.999999Z"
    }

    @Test
    fun `konverterer tidspunkt til localDate`() {
        1.januar(2020).startOfDay().toLocalDate() shouldBe LocalDate.of(2020, Month.JANUARY, 1)
        1.januar(2020).endOfDay().toLocalDate() shouldBe LocalDate.of(2020, Month.JANUARY, 1)
    }

    data class NestedSerialization(
        val tidspunkt: Tidspunkt,
        val other: String = "other values"
    )
}
