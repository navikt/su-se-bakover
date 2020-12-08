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

internal class EnsligBeregningTest {

    /**
     * Eksempel fra 01.05.2020
     +----------------+----------+----------------------------+----------+
     |     Bruker     |          |           Total            |          |
     +----------------+----------+----------------------------+----------+
     | Stønadssats    | 251.350  | SUM SU/år                  | =5.027   |
     | Arbeidsinntekt | -180.000 | SUM SU/mnd                 | =418,917 |
     | Folketrygd     | -66.323  | Utbetalt SU/år (avrundet)  | =5.028   |
     | SUM            | =5.027   | Utbetalt SU/mnd (avrundet) | =419     |
     +----------------+----------+----------------------------+----------+
     */
    @Test
    fun `beregningseksempel fra fagsiden`() {
        val periode = Periode(1.mai(2020), 30.april(2021))

        val arbeidsinntektPrÅr = 180000.0
        val folketrygdPrÅr = 66323.0
        val arbeidsinntektPrMnd = arbeidsinntektPrÅr / 12
        val folketrydPrMnd = folketrygdPrÅr / 12

        val beregningsgrunnlag = Beregningsgrunnlag(
            periode = periode,
            forventetInntektPerÅr = 0.0,
            fradragFraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = arbeidsinntektPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.OffentligPensjon,
                    beløp = folketrydPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        BeregningStrategy.BorAlene.beregn(beregningsgrunnlag).let {
            it.getSumYtelse() shouldBe 5028
            it.getSumFradrag() shouldBe (arbeidsinntektPrÅr + folketrygdPrÅr).plusOrMinus(0.5)
            it.getMånedsberegninger().forEach {
                it.getSumYtelse() shouldBe 419
            }
        }
    }
}
