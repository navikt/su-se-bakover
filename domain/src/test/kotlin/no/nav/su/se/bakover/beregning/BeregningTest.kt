package no.nav.su.se.bakover.beregning

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningsPeriode
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset

internal class BeregningTest {

    @Test
    fun `should handle variable period length`() {
        val fom = LocalDate.of(2020, Month.JANUARY, 1)
        val oneMonth = Beregning(
            fom = fom,
            tom = fom.plusMonths(1).minusDays(1),
            sats = Sats.HØY,
            fradrag = emptyList()
        ).toDto()
        oneMonth.månedsberegninger shouldHaveSize 1

        val threeMonths = Beregning(
            fom = fom,
            tom = fom.plusMonths(3).minusDays(1),
            sats = Sats.HØY,
            fradrag = emptyList()
        ).toDto()
        threeMonths.månedsberegninger shouldHaveSize 3

        val twelweMonths = Beregning(
            fom = fom,
            tom = fom.plusMonths(12).minusDays(1),
            sats = Sats.HØY,
            fradrag = emptyList()
        ).toDto()
        twelweMonths.månedsberegninger shouldHaveSize 12
    }

    @Test
    fun `throws if illegal arguments`() {
        assertThrows<IllegalArgumentException> {
            val fom = LocalDate.of(2020, Month.JANUARY, 14)
            Beregning(
                fom = fom,
                tom = fom.plusMonths(3),
                sats = Sats.HØY,
                fradrag = emptyList()
            )
        }

        assertThrows<IllegalArgumentException> {
            val fom = LocalDate.of(2020, Month.JANUARY, 1)
            Beregning(
                fom = fom,
                tom = LocalDate.of(2020, Month.JANUARY, 24),
                sats = Sats.HØY,
                fradrag = emptyList()
            )
        }

        assertThrows<IllegalArgumentException> {
            val fom = LocalDate.of(2020, Month.JANUARY, 1)
            Beregning(
                fom = fom,
                tom = fom.minusMonths(3),
                sats = Sats.HØY,
                fradrag = emptyList()
            )
        }
    }

    @Test
    fun `should not calculate months if already calculated`() {
        val beregning = Beregning(
            fom = LocalDate.of(2020, Month.JANUARY, 1),
            tom = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            månedsberegninger = mutableListOf(
                Månedsberegning(
                    fom = LocalDate.of(2020, Month.JANUARY, 1),
                    sats = Sats.HØY,
                    fradrag = 0
                )
            ),
            fradrag = emptyList()
        )
        beregning.toDto().månedsberegninger shouldHaveSize 1
    }

    @Test
    fun `sort by opprettet`() {
        val first = Beregning(
            fom = LocalDate.of(2020, Month.JANUARY, 1),
            tom = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toInstant(ZoneOffset.UTC),
            fradrag = emptyList()
        )
        val second = Beregning(
            fom = LocalDate.of(2020, Month.JANUARY, 1),
            tom = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 2, 15).toInstant(ZoneOffset.UTC),
            fradrag = emptyList()
        )
        val third = Beregning(
            fom = LocalDate.of(2020, Month.JANUARY, 1),
            tom = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 11, 59, 55).toInstant(ZoneOffset.UTC),
            fradrag = emptyList()
        )
        val beregninger = listOf(
            first,
            second,
            third
        )
        val sorted = beregninger.sortedWith(Beregning.Opprettet)
        sorted shouldContainInOrder listOf(third, first, second)
    }

    @Test
    fun `bruker riktig fradrag per måned`() {
        val b = Beregning(
            fom = LocalDate.of(2020, Month.JANUARY, 1),
            tom = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toInstant(ZoneOffset.UTC),
            fradrag = listOf(
                Fradrag(type = Fradragstype.Arbeidsinntekt, beløp = 12000),
                Fradrag(type = Fradragstype.Barnetillegg, beløp = 1200)
            )
        )
        b.toDto().månedsberegninger.forEach { it.fradrag shouldBe 1100 }
    }

    @Test
    fun `støtter ikke negative fradrag`() {
        shouldThrow<java.lang.IllegalArgumentException> {
            Beregning(
                fom = LocalDate.of(2020, Month.JANUARY, 1),
                tom = LocalDate.of(2020, Month.DECEMBER, 31),
                sats = Sats.HØY,
                opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toInstant(ZoneOffset.UTC),
                fradrag = listOf(
                    Fradrag(type = Fradragstype.Arbeidsinntekt, beløp = -100),
                    Fradrag(type = Fradragstype.Arbeidsinntekt, beløp = 200)
                )
            )
        }.also { it.message shouldContain "negativ" }
    }

    @Test
    fun `sjekk at beregning av 1 periode fungerer`() {
        val b = Beregning(
            fom = 1.januar(2020),
            tom = 31.desember(2020),
            sats = Sats.HØY,
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toInstant(ZoneOffset.UTC),
            fradrag = emptyList()
        )
        b.hentPerioder() shouldBe listOf(
            BeregningsPeriode(
                fom = 1.januar(2020),
                tom = 31.desember(2020),
                beløp = 20637,
                sats = Sats.HØY
            )
        )
    }

    @Test
    fun `sjekk at beregning av 2 periode fungerer`() {
        val b = Beregning(
            fom = 1.januar(2019),
            tom = 31.desember(2019),
            sats = Sats.HØY,
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toInstant(ZoneOffset.UTC),
            fradrag = emptyList()
        )
        b.hentPerioder() shouldBe listOf(
            BeregningsPeriode(
                fom = 1.januar(2019),
                tom = 30.april(2019),
                beløp = 20022,
                sats = Sats.HØY
            ),
            BeregningsPeriode(
                fom = 1.mai(2019),
                tom = 31.desember(2019),
                beløp = 20637,
                sats = Sats.HØY
            )
        )
    }
}
