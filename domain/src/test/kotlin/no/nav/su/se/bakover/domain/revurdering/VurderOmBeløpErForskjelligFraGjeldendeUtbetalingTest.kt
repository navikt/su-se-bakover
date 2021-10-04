package no.nav.su.se.bakover.domain.revurdering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class VurderOmBeløpErForskjelligFraGjeldendeUtbetalingTest {

    private val beregningsperiode = Periode.create(1.januar(2021), 30.april(2021))

    @Test
    fun `ingen utbetalinger kaster exception`() {
        val måneder = listOf(
            lagMaanedMock(januar(2021), 5000),
            lagMaanedMock(februar(2021), 5000),
            lagMaanedMock(mars(2021), 5000),
            lagMaanedMock(april(2021), 5000),
        )
        val beregning = mock<Beregning>() {
            on { this.periode } doReturn beregningsperiode
            on { getMånedsberegninger() } doReturn måneder
        }
        shouldThrow<IllegalStateException> {
            VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
                eksisterendeUtbetalinger = emptyList(),
                nyBeregning = beregning,
            ).resultat
        }
    }

    @Test
    fun `alle måneder med samme beløp som før gir false`() {
        val måneder = listOf(
            lagMaanedMock(januar(2021), 5000),
            lagMaanedMock(februar(2021), 5000),
            lagMaanedMock(mars(2021), 5000),
            lagMaanedMock(april(2021), 5000),
        )
        val beregning = mock<Beregning>() {
            on { this.periode } doReturn beregningsperiode
            on { getMånedsberegninger() } doReturn måneder
        }

        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(
                lagUtbetaling(
                    månedsbeløp = 5000,
                    periode = periode2021,
                ),
            ),
            nyBeregning = beregning,
        ).resultat shouldBe false
    }

    @Test
    fun `alle måneder med annet beløp enn tidligere gir true`() {
        val måneder = listOf(
            lagMaanedMock(januar(2021), 7500),
            lagMaanedMock(februar(2021), 7500),
            lagMaanedMock(mars(2021), 7500),
            lagMaanedMock(april(2021), 7500),
        )
        val beregning = mock<Beregning>() {
            on { this.periode } doReturn beregningsperiode
            on { getMånedsberegninger() } doReturn måneder
        }

        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(
                lagUtbetaling(
                    månedsbeløp = 5000,
                    periode = periode2021,
                ),
            ),
            nyBeregning = beregning,
        ).resultat shouldBe true
    }

    @Test
    fun `utbetalinger overlapper ikke med beregning kaster exception`() {
        val måneder = listOf(
            lagMaanedMock(januar(2021), 5000),
            lagMaanedMock(februar(2021), 5000),
            lagMaanedMock(mars(2021), 5000),
            lagMaanedMock(april(2021), 5000),
        )
        val beregning = mock<Beregning>() {
            on { this.periode } doReturn beregningsperiode
            on { getMånedsberegninger() } doReturn måneder
        }

        shouldThrow<IllegalStateException> {
            VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
                eksisterendeUtbetalinger = listOf(
                    lagUtbetaling(
                        månedsbeløp = 5000,
                        periode = januar(2021),
                    ),
                    lagUtbetaling(
                        månedsbeløp = 5000,
                        periode = mars(2021),
                    ),
                    lagUtbetaling(
                        månedsbeløp = 5000,
                        periode = april(2021),
                    ),
                ),
                nyBeregning = beregning,
            ).resultat
        }
    }

    @Test
    fun `forskjellige beløp gir ingen endring hvis de matcher`() {
        val måneder = listOf(
            lagMaanedMock(januar(2021), 1000),
            lagMaanedMock(februar(2021), 2000),
            lagMaanedMock(mars(2021), 3000),
            lagMaanedMock(april(2021), 4000),
        )
        val beregning = mock<Beregning>() {
            on { this.periode } doReturn beregningsperiode
            on { getMånedsberegninger() } doReturn måneder
        }

        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(
                lagUtbetaling(månedsbeløp = 1000, periode = januar(2021)),
                lagUtbetaling(månedsbeløp = 2000, periode = februar(2021)),
                lagUtbetaling(månedsbeløp = 3000, periode = mars(2021)),
                lagUtbetaling(månedsbeløp = 4000, periode = april(2021)),
            ),
            nyBeregning = beregning,
        ).resultat shouldBe false
    }

    @Test
    fun `forskjellige beløp gir endring hvis en av de er endret`() {
        val måneder = listOf(
            lagMaanedMock(januar(2021), 1000),
            lagMaanedMock(februar(2021), 2000),
            lagMaanedMock(mars(2021), 3001),
            lagMaanedMock(april(2021), 4000),
        )
        val beregning = mock<Beregning>() {
            on { this.periode } doReturn beregningsperiode
            on { getMånedsberegninger() } doReturn måneder
        }

        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(
                lagUtbetaling(månedsbeløp = 1000, periode = januar(2021)),
                lagUtbetaling(månedsbeløp = 2000, periode = februar(2021)),
                lagUtbetaling(månedsbeløp = 3000, periode = mars(2021)),
                lagUtbetaling(månedsbeløp = 4000, periode = april(2021)),
            ),
            nyBeregning = beregning,
        ).resultat shouldBe true
    }

    private fun lagMaanedMock(periode: Periode, sumYtelse: Int): Månedsberegning {
        return mock {
            on { this.periode } doReturn periode
            on { getSumYtelse() } doReturn sumYtelse
        }
    }

    @Test
    fun `en av månedene har endring fra tidligere gir true`() {
        val måneder1 = listOf(
            lagMaanedMock(januar(2021), 7500),
            lagMaanedMock(februar(2021), 5000),
            lagMaanedMock(mars(2021), 5000),
            lagMaanedMock(april(2021), 5000),
        )
        val beregning1 = mock<Beregning>() {
            on { this.periode } doReturn beregningsperiode
            on { getMånedsberegninger() } doReturn måneder1
        }

        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(
                lagUtbetaling(
                    månedsbeløp = 5000,
                    periode = periode2021,
                ),
            ),
            nyBeregning = beregning1,
        ).resultat shouldBe true

        val måneder2 = listOf(
            lagMaanedMock(januar(2021), 5000),
            lagMaanedMock(februar(2021), 5000),
            lagMaanedMock(mars(2021), 5000),
            lagMaanedMock(april(2021), 7500),
        )
        val beregning2 = mock<Beregning>() {
            on { this.periode } doReturn beregningsperiode
            on { getMånedsberegninger() } doReturn måneder2
        }

        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(
                lagUtbetaling(
                    månedsbeløp = 5000,
                    periode = periode2021,
                ),
            ),
            nyBeregning = beregning2,
        ).resultat shouldBe true

        val måneder3 = listOf(
            lagMaanedMock(januar(2021), 5000),
            lagMaanedMock(februar(2021), 2500),
            lagMaanedMock(mars(2021), 2500),
            lagMaanedMock(april(2021), 5000),
        )
        val beregning3 = mock<Beregning>() {
            on { this.periode } doReturn beregningsperiode
            on { getMånedsberegninger() } doReturn måneder3
        }

        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(
                lagUtbetaling(
                    månedsbeløp = 5000,
                    periode = periode2021,
                ),
            ),
            nyBeregning = beregning3,
        ).resultat shouldBe true
    }

    private fun lagUtbetaling(månedsbeløp: Int, periode: Periode = beregningsperiode) = Utbetalingslinje.Ny(
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = null,
        beløp = månedsbeløp,
    )
}
