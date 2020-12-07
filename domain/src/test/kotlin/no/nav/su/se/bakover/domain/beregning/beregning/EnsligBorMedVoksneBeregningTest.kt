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
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import org.junit.jupiter.api.Test

internal class EnsligBorMedVoksneBeregningTest {

    /**
     * Eksempel fra 01.05.2020
     +----------------------------+-------------+
     | Stønadssats                | 231.080     |
     | Arbeidsinntekt             | -20.000     |
     | Folketrygd                 | -14.256     |
     | Utenlandsk inntekt         | -40.927     |
     | -------------------------- | ----------- |
     | SUM SU/år                  | =155.897    |
     | SUM SU/mnd                 | 12.991,4167 |
     | Utbetalt SU/år (avrundet)  | 155.892     |
     | Utbetalt SU/mnd (avrundet) | 12.991      |
     +----------------------------+-------------+
     */
    @Test
    fun `beregningseksempel fra fagsiden`() {
        val periode = Periode(1.mai(2020), 30.april(2021))

        val arbeidsinntektPrÅr = 20000.0
        val folketrygdPrÅr = 14256.0
        val utenlandskInntektPrÅr = 40927.0

        val arbeidsinntektPrMnd = arbeidsinntektPrÅr / 12
        val folketrygdPrMnd = folketrygdPrÅr / 12
        val utenlandskInntektPrMnd = utenlandskInntektPrÅr / 12

        val beregningsgrunnlag = Beregningsgrunnlag(
            beregningsperiode = periode,
            fraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = arbeidsinntektPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.OffentligPensjon,
                    beløp = folketrygdPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.OffentligPensjon,
                    beløp = utenlandskInntektPrMnd,
                    periode = periode,
                    utenlandskInntekt = UtenlandskInntekt(
                        beløpIUtenlandskValuta = 10,
                        valuta = "Andebydollars",
                        kurs = 3.0
                    ),
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            forventetInntekt = 0.0
        )

        BeregningStrategy.BorMedVoksne.beregn(beregningsgrunnlag).let {
            it.getSumYtelse() shouldBe 155892
            it.getSumFradrag() shouldBe (arbeidsinntektPrÅr + folketrygdPrÅr + utenlandskInntektPrÅr).plusOrMinus(0.5)
            it.getMånedsberegninger().forEach {
                it.getSumYtelse() shouldBe 12991
            }
        }
    }
}
