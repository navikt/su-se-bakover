package no.nav.su.se.bakover.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.Month

internal class MånedsberegningTest {
    @Test
    fun `calculation 1`() {
        val dto = Månedsberegning(
            fom = LocalDate.of(2020, Month.JANUARY, 1),
            sats = Sats.HØY,
            fradrag = 0
        ).toDto()

        dto.fom shouldBe LocalDate.of(2020, Month.JANUARY, 1)
        dto.tom shouldBe LocalDate.of(2020, Month.JANUARY, 31)
        dto.grunnbeløp shouldBe 99858
        dto.sats shouldBe Sats.HØY
        dto.beløp shouldBe 20637
    }

    @Test
    fun `calculation 2`() {
        val dto = Månedsberegning(
            fom = LocalDate.of(2018, Month.MARCH, 1),
            sats = Sats.LAV,
            fradrag = 0
        ).toDto()

        dto.fom shouldBe LocalDate.of(2018, Month.MARCH, 1)
        dto.tom shouldBe LocalDate.of(2018, Month.MARCH, 31)
        dto.grunnbeløp shouldBe 93634
        dto.sats shouldBe Sats.LAV
        dto.beløp shouldBe 17790
    }

    @Test
    fun `trekker fra fradrag`() {
        val dto = Månedsberegning(
            fom = LocalDate.of(2018, Month.MARCH, 1),
            sats = Sats.LAV,
            fradrag = 100
        ).toDto()

        dto.fom shouldBe LocalDate.of(2018, Month.MARCH, 1)
        dto.tom shouldBe LocalDate.of(2018, Month.MARCH, 31)
        dto.grunnbeløp shouldBe 93634
        dto.sats shouldBe Sats.LAV
        dto.beløp shouldBe 17690
    }

    @Test
    fun `beløp kan ikke bli negativt pga fradrag`() {
        val dto = Månedsberegning(
            fom = LocalDate.of(2018, Month.MARCH, 1),
            sats = Sats.LAV,
            fradrag = Int.MAX_VALUE
        ).toDto()

        dto.fom shouldBe LocalDate.of(2018, Month.MARCH, 1)
        dto.tom shouldBe LocalDate.of(2018, Month.MARCH, 31)
        dto.grunnbeløp shouldBe 93634
        dto.sats shouldBe Sats.LAV
        dto.beløp shouldBe 0
    }

    @Test
    fun `uses grunnbeløp based on date`() {
        val old = Månedsberegning(
            fom = LocalDate.of(2018, Month.MARCH, 1),
            sats = Sats.LAV,
            fradrag = 0
        ).toDto()

        old.grunnbeløp shouldBe 93634
        old.beløp shouldBe 17790

        val new = Månedsberegning(
            fom = LocalDate.of(2018, Month.SEPTEMBER, 1),
            sats = Sats.LAV,
            fradrag = 0
        ).toDto()

        new.grunnbeløp shouldBe 96883
        new.beløp shouldBe 18408
    }

    @Test
    fun `throws if illegal values`() {
        assertThrows<IllegalArgumentException> {
            val fom = LocalDate.of(2020, Month.JANUARY, 14)
            Månedsberegning(
                fom = fom,
                tom = fom.plusMonths(3),
                sats = Sats.HØY,
                fradrag = 0
            )
        }

        assertThrows<IllegalArgumentException> {
            val fom = LocalDate.of(2020, Month.JANUARY, 1)
            Månedsberegning(
                fom = fom,
                tom = LocalDate.of(2020, Month.JANUARY, 24),
                sats = Sats.HØY,
                fradrag = 0
            )
        }
    }
}
