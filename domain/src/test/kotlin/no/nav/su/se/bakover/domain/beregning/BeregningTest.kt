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
        val fraOgMed = LocalDate.of(2020, Month.JANUARY, 1)
        val oneMonth = Beregning(
            fraOgMed = fraOgMed,
            tilOgMed = fraOgMed.plusMonths(1).minusDays(1),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
        oneMonth.månedsberegninger shouldHaveSize 1

        val threeMonths = Beregning(
            fraOgMed = fraOgMed,
            tilOgMed = fraOgMed.plusMonths(3).minusDays(1),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
        threeMonths.månedsberegninger shouldHaveSize 3

        val twelweMonths = Beregning(
            fraOgMed = fraOgMed,
            tilOgMed = fraOgMed.plusMonths(12).minusDays(1),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
        twelweMonths.månedsberegninger shouldHaveSize 12
    }

    @Test
    fun `throws if illegal arguments`() {
        assertThrows<IllegalArgumentException> {
            val fraOgMed = LocalDate.of(2020, Month.JANUARY, 14)
            Beregning(
                fraOgMed = fraOgMed,
                tilOgMed = fraOgMed.plusMonths(3),
                sats = Sats.HØY,
                fradrag = emptyList()
            )
        }

        assertThrows<IllegalArgumentException> {
            val fraOgMed = LocalDate.of(2020, Month.JANUARY, 1)
            Beregning(
                fraOgMed = fraOgMed,
                tilOgMed = LocalDate.of(2020, Month.JANUARY, 24),
                sats = Sats.HØY,
                fradrag = emptyList()
            )
        }

        assertThrows<IllegalArgumentException> {
            val fraOgMed = LocalDate.of(2020, Month.JANUARY, 1)
            Beregning(
                fraOgMed = fraOgMed,
                tilOgMed = fraOgMed.minusMonths(3),
                sats = Sats.HØY,
                fradrag = emptyList()
            )
        }
    }

    @Test
    fun `should not calculate months if already calculated`() {
        val beregning = Beregning(
            fraOgMed = LocalDate.of(2020, Month.JANUARY, 1),
            tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            fradrag = emptyList(),
            månedsberegninger = mutableListOf(
                Månedsberegning(
                    fraOgMed = LocalDate.of(2020, Month.JANUARY, 1),
                    sats = Sats.HØY,
                    fradrag = 0
                )
            )
        )
        beregning.månedsberegninger shouldHaveSize 1
    }

    @Test
    fun `sort by opprettet`() {
        val first = Beregning(
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toTidspunkt(),
            fraOgMed = LocalDate.of(2020, Month.JANUARY, 1),
            tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
        val second = Beregning(
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 2, 15).toTidspunkt(),
            fraOgMed = LocalDate.of(2020, Month.JANUARY, 1),
            tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
        val third = Beregning(
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 11, 59, 55).toTidspunkt(),
            fraOgMed = LocalDate.of(2020, Month.JANUARY, 1),
            tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
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
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toTidspunkt(),
            fraOgMed = LocalDate.of(2020, Month.JANUARY, 1),
            tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            fradrag = listOf(
                Fradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 12000,
                    utenlandskInntekt = null,
                    inntektDelerAvPeriode = null,
                ),
                Fradrag(
                    type = Fradragstype.Barnetillegg,
                    beløp = 1200,
                    utenlandskInntekt = null,
                    inntektDelerAvPeriode = null,
                )
            )
        )
        b.månedsberegninger.forEach { it.fradrag shouldBe 1100 }
    }

    @Test
    fun `støtter ikke negative fradrag`() {
        shouldThrow<java.lang.IllegalArgumentException> {
            Beregning(
                opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toTidspunkt(),
                fraOgMed = LocalDate.of(2020, Month.JANUARY, 1),
                tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31),
                sats = Sats.HØY,
                fradrag = listOf(
                    Fradrag(
                        type = Fradragstype.Arbeidsinntekt,
                        beløp = -100,
                        utenlandskInntekt = null,
                        inntektDelerAvPeriode = null,
                    ),
                    Fradrag(
                        type = Fradragstype.Arbeidsinntekt,
                        beløp = 200,
                        utenlandskInntekt = null,
                        inntektDelerAvPeriode = null,
                    )
                )
            )
        }.also { it.message shouldContain "negativ" }
    }

    @Test
    fun `beregnet beløp er over null men under minstebeløp, for en måned`() {
        val høyInntekt = 242695 // 98% av full supplerende stønad (Høy sats) for 2019

        val b = Beregning(
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toTidspunkt(),
            fraOgMed = LocalDate.of(2020, Month.JANUARY, 1),
            tilOgMed = LocalDate.of(2020, Month.JANUARY, 31),
            sats = Sats.HØY,
            fradrag = listOf(
                Fradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = høyInntekt,
                    utenlandskInntekt = null,
                    inntektDelerAvPeriode = null
                ),
            )
        )

        b.beløpErOverNullMenUnderMinstebeløp() shouldBe true
    }

    @Test
    fun `beregnet beløp er rett under minstebeløp`() {
        // 98% av full supplerende stønad (Høy sats) for
        // 2019 - Jan, Feb, Mars, Apr
        // 2020 - Maj, Juni, Juli, Aug, Sep, Okt, Nov, Dec
        val høyInntekt = 245114 + 8 // pågrundav avrundning så må vi legge på 8

        val b = Beregning(
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toTidspunkt(),
            fraOgMed = LocalDate.of(2020, Month.JANUARY, 1),
            tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            fradrag = listOf(
                Fradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = høyInntekt,
                    utenlandskInntekt = null,
                    inntektDelerAvPeriode = null
                ),
            )
        )

        b.beløpErOverNullMenUnderMinstebeløp() shouldBe true
    }

    @Test
    fun `beregnet beløp er akkurat på minstebeløp`() {
        // 98% av full supplerende stønad (Høy sats) for
        // 2019 - Jan, Feb, Mars, Apr
        // 2020 - Maj, Juni, Juli, Aug, Sep, Okt, Nov, Dec
        val høyInntekt = 245114

        val b = Beregning(
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toTidspunkt(),
            fraOgMed = LocalDate.of(2020, Month.JANUARY, 1),
            tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            fradrag = listOf(
                Fradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = høyInntekt,
                    utenlandskInntekt = null,
                    inntektDelerAvPeriode = null
                ),
            )
        )

        b.beløpErOverNullMenUnderMinstebeløp() shouldBe false
    }
}
