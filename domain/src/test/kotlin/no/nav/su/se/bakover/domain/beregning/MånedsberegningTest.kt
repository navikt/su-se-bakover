package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.Month

internal class MånedsberegningTest {
    @Test
    fun `calculation 1`() {
        val månedsberegning = Månedsberegning(
            fraOgMed = LocalDate.of(2020, Month.JANUARY, 1),
            sats = Sats.HØY,
            fradrag = 0
        )

        månedsberegning.fraOgMed shouldBe LocalDate.of(2020, Month.JANUARY, 1)
        månedsberegning.tilOgMed shouldBe LocalDate.of(2020, Month.JANUARY, 31)
        månedsberegning.grunnbeløp shouldBe 99858
        månedsberegning.sats shouldBe Sats.HØY
        månedsberegning.beløp shouldBe 20637
    }

    @Test
    fun `calculation 2`() {
        val månedsberegning = Månedsberegning(
            fraOgMed = LocalDate.of(2018, Month.MARCH, 1),
            sats = Sats.ORDINÆR,
            fradrag = 0
        )

        månedsberegning.fraOgMed shouldBe LocalDate.of(2018, Month.MARCH, 1)
        månedsberegning.tilOgMed shouldBe LocalDate.of(2018, Month.MARCH, 31)
        månedsberegning.grunnbeløp shouldBe 93634
        månedsberegning.sats shouldBe Sats.ORDINÆR
        månedsberegning.beløp shouldBe 17790
    }

    @Test
    fun `trekker fra fradrag`() {
        val månedsberegning = Månedsberegning(
            fraOgMed = LocalDate.of(2018, Month.MARCH, 1),
            sats = Sats.ORDINÆR,
            fradrag = 100
        )

        månedsberegning.fraOgMed shouldBe LocalDate.of(2018, Month.MARCH, 1)
        månedsberegning.tilOgMed shouldBe LocalDate.of(2018, Month.MARCH, 31)
        månedsberegning.grunnbeløp shouldBe 93634
        månedsberegning.sats shouldBe Sats.ORDINÆR
        månedsberegning.beløp shouldBe 17690
    }

    @Test
    fun `beløp kan ikke bli negativt pga fradrag`() {
        val månedsberegning = Månedsberegning(
            fraOgMed = LocalDate.of(2018, Month.MARCH, 1),
            sats = Sats.ORDINÆR,
            fradrag = Int.MAX_VALUE
        )

        månedsberegning.fraOgMed shouldBe LocalDate.of(2018, Month.MARCH, 1)
        månedsberegning.tilOgMed shouldBe LocalDate.of(2018, Month.MARCH, 31)
        månedsberegning.grunnbeløp shouldBe 93634
        månedsberegning.sats shouldBe Sats.ORDINÆR
        månedsberegning.beløp shouldBe 0
    }

    @Test
    fun `uses grunnbeløp based on date`() {
        val old = Månedsberegning(
            fraOgMed = LocalDate.of(2018, Month.MARCH, 1),
            sats = Sats.ORDINÆR,
            fradrag = 0
        )

        old.grunnbeløp shouldBe 93634
        old.beløp shouldBe 17790

        val new = Månedsberegning(
            fraOgMed = LocalDate.of(2018, Month.SEPTEMBER, 1),
            sats = Sats.ORDINÆR,
            fradrag = 0
        )

        new.grunnbeløp shouldBe 96883
        new.beløp shouldBe 18408
    }

    @Test
    fun `throws if illegal values`() {
        assertThrows<IllegalArgumentException> {
            val fraOgMed = LocalDate.of(2020, Month.JANUARY, 14)
            Månedsberegning(
                fraOgMed = fraOgMed,
                tilOgMed = fraOgMed.plusMonths(3),
                sats = Sats.HØY,
                fradrag = 0
            )
        }

        assertThrows<IllegalArgumentException> {
            val fraOgMed = LocalDate.of(2020, Month.JANUARY, 1)
            Månedsberegning(
                fraOgMed = fraOgMed,
                tilOgMed = LocalDate.of(2020, Month.JANUARY, 24),
                sats = Sats.HØY,
                fradrag = 0
            )
        }
    }
}
