package no.nav.su.se.bakover.beregning

import io.kotest.matchers.collections.shouldHaveSize
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.Month

internal class BeregningTest {

    @Test
    fun `should handle variable period length`() {
        val fom = LocalDate.of(2020, Month.JANUARY, 1)
        val oneMonth = Beregning(
            fom = fom,
            tom = fom.plusMonths(1).minusDays(1),
            sats = Sats.HØY
        ).toDto()
        oneMonth.månedsberegninger shouldHaveSize 1

        val threeMonths = Beregning(
            fom = fom,
            tom = fom.plusMonths(3).minusDays(1),
            sats = Sats.HØY
        ).toDto()
        threeMonths.månedsberegninger shouldHaveSize 3

        val twelweMonths = Beregning(
            fom = fom,
            tom = fom.plusMonths(12).minusDays(1),
            sats = Sats.HØY
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
                sats = Sats.HØY
            )
        }

        assertThrows<IllegalArgumentException> {
            val fom = LocalDate.of(2020, Month.JANUARY, 1)
            Beregning(
                fom = fom,
                tom = LocalDate.of(2020, Month.JANUARY, 24),
                sats = Sats.HØY
            )
        }

        assertThrows<IllegalArgumentException> {
            val fom = LocalDate.of(2020, Month.JANUARY, 1)
            Beregning(
                fom = fom,
                tom = fom.minusMonths(3),
                sats = Sats.HØY
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
                    sats = Sats.HØY
                )
            )
        )
        beregning.toDto().månedsberegninger shouldHaveSize 1
    }
}
