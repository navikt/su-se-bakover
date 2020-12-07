package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class EpsOver67BeregningTest {

    /**
     * Eksempel fra 01.05.2020
     +----------------+----------+-------------+----------+----------------------------+------------+
     |     Bruker     |          |     EPS     |          |           Total            |            |
     +----------------+----------+-------------+----------+----------------------------+------------+
     | Stønadssats    | 231.080  | Folketrygd  |  190.000 | SUM Bruker                 | 39.999     |
     | Arbeidsinntekt | -71.081  | MPN ordinær | -183.587 | Fradrag EPS                | -6.413     |
     | Folketrygd     | -120.000 | SUM         |    6.413 | SUM SU/år                  | =33.586    |
     | SUM            | =39.999  |             |          | SUM SU/mnd                 | =2.798,857 |
     |                |          |             |          | Utbetalt SU/år (avrundet)  | =33.588    |
     |                |          |             |          | Utbetalt SU/mnd (avrundet) | =2.799     |
     +----------------+----------+-------------+----------+----------------------------+------------+
     */
    @Test
    fun `beregningseksempel fra fagsiden`() {
        val periode = Periode(1.mai(2020), 30.april(2021))
        val arbeidsinntekt = 71081.0
        val folketrygd = 120000.0
        val epsFolketryd = 190000.0
        val mpnOrdinær = 183587.0
        val beregningsgrunnlag = Beregningsgrunnlag(
            beregningsperiode = periode,
            fraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = arbeidsinntekt,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.OffentligPensjon,
                    beløp = folketrygd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.OffentligPensjon,
                    beløp = epsFolketryd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                )
            ),
            forventetInntekt = 0.0
        )

        BeregningStrategy.EpsOver67År.beregn(beregningsgrunnlag).let {
            it.getSumYtelse() shouldBe 33588
            it.getSumFradrag() shouldBe (arbeidsinntekt + folketrygd + (epsFolketryd - mpnOrdinær)).plusOrMinus(0.5)
            it.getMånedsberegninger().forEach {
                it.getSumYtelse() shouldBe 2799
            }
        }
    }
}
