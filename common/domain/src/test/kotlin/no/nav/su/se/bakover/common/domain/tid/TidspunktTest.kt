package no.nav.su.se.bakover.common.domain.tid

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldNotBeLessThan
import io.kotest.matchers.ints.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.between
import no.nav.su.se.bakover.common.tid.compareTo
import no.nav.su.se.bakover.common.tid.toTidspunkt
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class TidspunktTest {

    private val instant = Instant.parse("1970-01-01T01:01:01.123456789Z")

    @Test
    fun `truncate instant to same format as repo, precision in micros`() {
        val tidspunkt = instant.toTidspunkt()
        ChronoUnit.MICROS.between(instant, tidspunkt) shouldBe 0
        instant.toString().length.shouldNotBeLessThan(tidspunkt.toString().length)
        tidspunkt.nano % 1000 shouldBe 0

        val addedMicrosInstant = instant.plus(251, ChronoUnit.MICROS)
        val addedMicrosTidspunkt = addedMicrosInstant.toTidspunkt()
        ChronoUnit.MICROS.between(addedMicrosInstant, addedMicrosTidspunkt) shouldBe 0
        addedMicrosInstant.toString().length.shouldNotBeLessThan(addedMicrosTidspunkt.toString().length)
        addedMicrosTidspunkt.nano % 1000 shouldBe 0

        val addedNanosInstant = instant.plusNanos(378)
        val addedNanosTidspunkt = addedNanosInstant.toTidspunkt()
        (addedNanosInstant.nano - addedNanosTidspunkt.nano).shouldBeBetween(1, 1000)
        addedNanosInstant shouldNotBe addedNanosTidspunkt.instant
        addedNanosTidspunkt shouldBe addedNanosInstant
        ChronoUnit.MICROS.between(addedNanosInstant, addedNanosTidspunkt) shouldBe 0
        addedNanosInstant.toString().length.shouldNotBeLessThan(addedNanosTidspunkt.toString().length)
        addedNanosTidspunkt.nano % 1000 shouldBe 0
    }

    @Test
    fun `should equal instant truncated to same precision`() {
        val instant = instant.plusNanos(515)
        val tidspunkt = instant.toTidspunkt()
        instant shouldNotBe tidspunkt.instant
        instant.truncatedTo(ChronoUnit.MICROS) shouldBe tidspunkt.instant
        tidspunkt shouldBe instant
        tidspunkt shouldBe instant.truncatedTo(ChronoUnit.MICROS)
        val othertidspunkt = instant.toTidspunkt()
        tidspunkt shouldBe othertidspunkt
        tidspunkt shouldBe tidspunkt
    }

    @Test
    fun comparison() {
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
        val serialized = serialize(start)
        serialized shouldBe "\"2020-10-01T21:59:59.999999Z\"" // Denne serialiseres til json-streng istedenfor objekt
        val deserialized = deserialize<Tidspunkt>(serialized)
        deserialized shouldBe start

        val objSerialized = serialize(NestedSerialization(start))
        objSerialized shouldBe """{"tidspunkt":"2020-10-01T21:59:59.999999Z","other":"other values"}"""
        val objDeserialized = deserialize<NestedSerialization>(objSerialized)
        objDeserialized shouldBe NestedSerialization(start, "other values")
    }

    @Test
    fun januar2020() {
        val fixedUTC = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
        val nowUTC = Tidspunkt.now(fixedUTC)
        nowUTC.toString() shouldBe "2020-01-01T23:59:59.999999Z"

        val fixedOslo = Clock.fixed(1.januar(2020).endOfDay(zoneIdOslo).instant, zoneIdOslo)
        val nowOslo = Tidspunkt.now(fixedOslo)
        nowOslo.toString() shouldBe "2020-01-01T22:59:59.999999Z"
    }

    @Test
    fun `konverterer tidspunkt til localDate`() {
        1.januar(2020).startOfDay(ZoneOffset.UTC).toLocalDate(ZoneOffset.UTC) shouldBe LocalDate.of(
            2020,
            Month.JANUARY,
            1,
        )
        1.januar(2020).endOfDay(ZoneOffset.UTC).toLocalDate(ZoneOffset.UTC) shouldBe LocalDate.of(
            2020,
            Month.JANUARY,
            1,
        )
        1.januar(2020).startOfDay(zoneIdOslo).toLocalDate(zoneIdOslo) shouldBe LocalDate.of(2020, Month.JANUARY, 1)
        1.januar(2020).endOfDay(zoneIdOslo).toLocalDate(zoneIdOslo) shouldBe LocalDate.of(2020, Month.JANUARY, 1)
    }

    @Test
    fun `plusser på tid`() {
        Tidspunkt.create(instant).plus(1, ChronoUnit.DAYS).toString() shouldBe "1970-01-02T01:01:01.123456Z"
    }

    @Test
    fun `compare against self and instant`() {
        val tidligere = Instant.parse("2021-01-01T01:01:01.123456Z")
        val tidligereTidspunkt = Tidspunkt.create(tidligere)
        val senere = Instant.parse("2021-01-02T01:01:01.123456Z")
        val senereTidspunkt = Tidspunkt.create(senere)

        (tidligereTidspunkt > tidligereTidspunkt) shouldBe false
        (tidligereTidspunkt >= tidligereTidspunkt) shouldBe true
        (tidligereTidspunkt > tidligere) shouldBe false
        (tidligereTidspunkt >= tidligere) shouldBe true

        (tidligereTidspunkt < tidligereTidspunkt) shouldBe false
        (tidligereTidspunkt <= tidligereTidspunkt) shouldBe true
        (tidligereTidspunkt < tidligere) shouldBe false
        (tidligereTidspunkt <= tidligere) shouldBe true

        (tidligere > tidligereTidspunkt) shouldBe false
        (tidligere >= tidligereTidspunkt) shouldBe true
        (tidligere > tidligere) shouldBe false
        (tidligere >= tidligere) shouldBe true

        (tidligere < tidligereTidspunkt) shouldBe false
        (tidligere <= tidligereTidspunkt) shouldBe true
        (tidligere < tidligere) shouldBe false
        (tidligere <= tidligere) shouldBe true

        tidligereTidspunkt.compareTo(tidligereTidspunkt) shouldBe 0
        tidligereTidspunkt.compareTo(tidligere) shouldBe 0
        tidligere.compareTo(tidligereTidspunkt) shouldBe 0
        tidligere.compareTo(tidligere) shouldBe 0

        (tidligereTidspunkt > senereTidspunkt) shouldBe false
        (tidligereTidspunkt >= senereTidspunkt) shouldBe false
        (tidligereTidspunkt > senere) shouldBe false
        (tidligereTidspunkt >= senere) shouldBe false

        (tidligereTidspunkt < senereTidspunkt) shouldBe true
        (tidligereTidspunkt <= senereTidspunkt) shouldBe true
        (tidligereTidspunkt < senere) shouldBe true
        (tidligereTidspunkt <= senere) shouldBe true

        (tidligere > senereTidspunkt) shouldBe false
        (tidligere >= senereTidspunkt) shouldBe false
        (tidligere > senere) shouldBe false
        (tidligere >= senere) shouldBe false

        (tidligere < senereTidspunkt) shouldBe true
        (tidligere <= senereTidspunkt) shouldBe true
        (tidligere < senere) shouldBe true
        (tidligere <= senere) shouldBe true

        tidligereTidspunkt.compareTo(senereTidspunkt) shouldBe -1
        tidligereTidspunkt.compareTo(senere) shouldBe -1
        tidligere.compareTo(senereTidspunkt) shouldBe -1
        tidligere.compareTo(senere) shouldBe -1
        senereTidspunkt.compareTo(tidligereTidspunkt) shouldBe 1
        senereTidspunkt.compareTo(tidligere) shouldBe 1
        senere.compareTo(tidligereTidspunkt) shouldBe 1
        senere.compareTo(tidligere) shouldBe 1
    }

    data class NestedSerialization(
        val tidspunkt: Tidspunkt,
        val other: String = "other values",
    )

    @Test
    fun `instants between others`() {
        val sept5 = 5.september(2020).startOfDay()
        sept5.between(
            fraOgMed = 5.september(2020).startOfDay(),
            tilOgMed = 5.september(2020).endOfDay(),
        ) shouldBe true

        sept5.between(
            fraOgMed = 4.september(2020).startOfDay(),
            tilOgMed = 5.september(2020).startOfDay(),
        ) shouldBe true

        sept5.between(
            fraOgMed = 1.september(2020).startOfDay(),
            tilOgMed = 10.september(2020).startOfDay(),
        ) shouldBe true

        sept5.between(
            fraOgMed = 1.januar(2020).startOfDay(),
            tilOgMed = 10.januar(2020).startOfDay(),
        ) shouldBe false

        sept5.between(
            fraOgMed = 1.desember(2020).startOfDay(),
            tilOgMed = 10.desember(2020).startOfDay(),
        ) shouldBe false
    }
}
