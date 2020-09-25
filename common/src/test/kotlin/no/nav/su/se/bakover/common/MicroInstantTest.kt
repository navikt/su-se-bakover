package no.nav.su.se.bakover.common

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class MicroInstantTest {
    @Test
    fun `truncate instant to same format as repo, precision in micros`() {
        val instant = Instant.now()
        val microInstant = instant.toMicroInstant()
        instant.toEpochMilli() - microInstant.toEpochMilli() shouldBe 0
        instant.toString() shouldHaveLength microInstant.toString().length

        val addedMicros = instant.plus(251, ChronoUnit.MICROS)
        val microAddedMicros = addedMicros.toMicroInstant()
        addedMicros shouldBe microAddedMicros.instant
        microAddedMicros shouldBe addedMicros

        val addedNanos = instant.plusNanos(378)
        val microAddedNanos = addedNanos.toMicroInstant()
        addedNanos.nano - microAddedNanos.nano shouldBeGreaterThan 0
        addedNanos shouldNotBe microAddedNanos.instant
        microAddedNanos shouldBe addedNanos
    }

    @Test
    fun `should equal instant truncated to same precision`() {
        val instant = Instant.now().plusNanos(515)
        val microInstant = instant.toMicroInstant()
        instant shouldNotBe microInstant.instant
        instant.truncatedTo(MicroInstant.unit) shouldBe microInstant.instant
        microInstant shouldBe instant
        microInstant shouldBe instant.truncatedTo(MicroInstant.unit)
        val otherMicroInstant = instant.toMicroInstant()
        microInstant shouldBe otherMicroInstant
        microInstant shouldBe microInstant
    }

    @Test
    fun `comparison`() {
        val startOfDayInstant = 1.januar(2020).atStartOfDay().toInstant(ZoneOffset.UTC)
        val endOfDayInstant = 1.januar(2020).plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC)
        val startOfDayMicroInstant = startOfDayInstant.toMicroInstant()
        val endOfDayMicroInstant = endOfDayInstant.toMicroInstant()

        startOfDayInstant shouldBeLessThan endOfDayInstant
        startOfDayMicroInstant.instant shouldBeLessThan endOfDayMicroInstant.instant
        startOfDayInstant shouldBeLessThan endOfDayMicroInstant.instant
        endOfDayInstant shouldBeGreaterThan startOfDayMicroInstant.instant
    }

    @Test
    fun `new instances for companions`() {
        val firstEpoch = MicroInstant.EPOCH
        val secondEpoch = MicroInstant.EPOCH
        firstEpoch shouldNotBeSameInstanceAs secondEpoch
        val firstMin = MicroInstant.MIN
        val secondMin = MicroInstant.MIN
        firstMin shouldNotBeSameInstanceAs secondMin
    }

    @Test
    fun `should serialize as instant`() {
        val start = 1.oktober(2020).endOfDay()
        val serialized = objectMapper.writeValueAsString(start)
        val ske = objectMapper.writeValueAsString(Instant.now())
        println(ske)
        serialized shouldBe "\"2020-10-01T23:59:59.999999Z\"" // TODO Spennende at denne serialiseres til double qoutes?
        val deserialized = objectMapper.readValue(serialized, MicroInstant::class.java)
        deserialized shouldBe start

        val objSerialized = objectMapper.writeValueAsString(NestedSerialization(start))
        objSerialized shouldBe """{"microInstant":"2020-10-01T23:59:59.999999Z","other":"other values"}"""
        val objDeserialized = objectMapper.readValue(objSerialized, NestedSerialization::class.java)
        objDeserialized shouldBe NestedSerialization(start, "other values")
    }

    data class NestedSerialization(
        val microInstant: MicroInstant,
        val other: String = "other values"
    )
}
