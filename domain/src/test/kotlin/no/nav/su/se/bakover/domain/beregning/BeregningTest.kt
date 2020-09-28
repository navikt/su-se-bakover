package no.nav.su.se.bakover.domain.beregning

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.toTidspunkt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month

internal class BeregningTest {

    @Test
    fun `should handle variable period length`() {
        val fom = LocalDate.of(2020, Month.JANUARY, 1)
        val oneMonth = Beregning(
            fom = fom,
            tom = fom.plusMonths(1).minusDays(1),
            sats = Sats.HØY,
            fradrag = emptyList(),
            forventetInntekt = 500
        )
        oneMonth.månedsberegninger shouldHaveSize 1

        val threeMonths = Beregning(
            fom = fom,
            tom = fom.plusMonths(3).minusDays(1),
            sats = Sats.HØY,
            fradrag = emptyList(),
            forventetInntekt = 500
        )
        threeMonths.månedsberegninger shouldHaveSize 3

        val twelweMonths = Beregning(
            fom = fom,
            tom = fom.plusMonths(12).minusDays(1),
            sats = Sats.HØY,
            fradrag = emptyList(),
            forventetInntekt = 500
        )
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
                fradrag = emptyList(),
                forventetInntekt = 500
            )
        }

        assertThrows<IllegalArgumentException> {
            val fom = LocalDate.of(2020, Month.JANUARY, 1)
            Beregning(
                fom = fom,
                tom = LocalDate.of(2020, Month.JANUARY, 24),
                sats = Sats.HØY,
                fradrag = emptyList(),
                forventetInntekt = 500
            )
        }

        assertThrows<IllegalArgumentException> {
            val fom = LocalDate.of(2020, Month.JANUARY, 1)
            Beregning(
                fom = fom,
                tom = fom.minusMonths(3),
                sats = Sats.HØY,
                fradrag = emptyList(),
                forventetInntekt = 500
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
            fradrag = emptyList(),
            forventetInntekt = 500
        )
        beregning.månedsberegninger shouldHaveSize 1
    }

    @Test
    fun `sort by opprettet`() {
        val first = Beregning(
            fom = LocalDate.of(2020, Month.JANUARY, 1),
            tom = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toTidspunkt(),
            fradrag = emptyList(),
            forventetInntekt = 500
        )
        val second = Beregning(
            fom = LocalDate.of(2020, Month.JANUARY, 1),
            tom = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 2, 15).toTidspunkt(),
            fradrag = emptyList(),
            forventetInntekt = 500
        )
        val third = Beregning(
            fom = LocalDate.of(2020, Month.JANUARY, 1),
            tom = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 11, 59, 55).toTidspunkt(),
            fradrag = emptyList(),
            forventetInntekt = 500
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
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toTidspunkt(),
            fradrag = listOf(
                Fradrag(type = Fradragstype.Arbeidsinntekt, beløp = 12000),
                Fradrag(type = Fradragstype.Barnetillegg, beløp = 1200)
            ),
            forventetInntekt = 500
        )
        b.månedsberegninger.forEach { it.fradrag shouldBe 1100 }
    }

    @Test
    fun `støtter ikke negative fradrag`() {
        shouldThrow<java.lang.IllegalArgumentException> {
            Beregning(
                fom = LocalDate.of(2020, Month.JANUARY, 1),
                tom = LocalDate.of(2020, Month.DECEMBER, 31),
                sats = Sats.HØY,
                opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toTidspunkt(),
                fradrag = listOf(
                    Fradrag(type = Fradragstype.Arbeidsinntekt, beløp = -100),
                    Fradrag(type = Fradragstype.Arbeidsinntekt, beløp = 200)
                ),
                forventetInntekt = 500
            )
        }.also { it.message shouldContain "negativ" }
    }
}
