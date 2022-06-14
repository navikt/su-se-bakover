package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test

internal class EpsUnder67BeregningTest {
    /**
     * Eksempel fra 01.05.2020
     +-------------+----------+------------+---------+----------------------------+------------+
     |   Bruker    |          |    EPS     |         |           Total            |            |
     +-------------+----------+------------+---------+----------------------------+------------+
     | Stønadssats | 251.350  | Folketrygd | 98.880  | SUM Bruker                 |    182.578 |
     | Folketrygd  | -68.772  |            |         | Fradrag EPS                |    -98.880 |
     | Sum         | =182.578 | Sum        | =98.880 | SUM SU/år                  |     83.698 |
     |             |          |            |         | SUM SU/mnd                 | 6.794,8333 |
     |             |          |            |         | Utbetalt SU/år (avrundet)  |     83.700 |
     |             |          |            |         | Utbetalt SU/mnd (avrundet) |      6.975 |
     +-------------+----------+------------+---------+----------------------------+------------+
     */
    @Test
    fun `beregningseksempel fra fagsiden`() {
        val periode = Periode.create(1.mai(2020), 30.april(2021))

        val folketrygdPrÅr = 68772.0
        val folketrygdEpsPrÅr = 98880.0

        val folketrygdPrMnd = folketrygdPrÅr / 12
        val folketrygdEpsPrMnd = folketrygdEpsPrÅr / 12

        val beregningsgrunnlag = Beregningsgrunnlag.create(
            beregningsperiode = periode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = periode,
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 0,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.OffentligPensjon,
                    månedsbeløp = folketrygdPrMnd,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER
                ),
                lagFradragsgrunnlag(
                    type = Fradragstype.OffentligPensjon,
                    månedsbeløp = folketrygdEpsPrMnd,
                    periode = periode,
                    tilhører = FradragTilhører.EPS
                ),
            )
        )
        BeregningFactory(fixedClock).ny(
            fradrag = beregningsgrunnlag.fradrag,
            begrunnelse = null,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = beregningsgrunnlag.beregningsperiode,
                    strategy = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato()),
                )
            ),
        ).let {
            it.getSumYtelse() shouldBe 83700
            it.getSumFradrag() shouldBe (folketrygdPrÅr + folketrygdEpsPrÅr).plusOrMinus(0.5)
            it.getMånedsberegninger().forEach {
                it.getSumYtelse() shouldBe 6975
            }
        }
    }
}
