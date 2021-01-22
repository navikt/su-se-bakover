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

internal class EpsUnder67OgUførFlyktningBeregningTest {

    /**
     * Eksempel fra 01.05.2020
     +----------------+----------+---------------------+----------+----------------------------+---------+
     |     Bruker     |          |         EPS         |          |           Total            |         |
     +----------------+----------+---------------------+----------+----------------------------+---------+
     | Stønadssats    | 231.080  | Folketrygd          | 190.000  | SUM Bruker                 | =90.488 |
     | Arbeidsinntekt | -12.000  | Annen norsk penjson | 45.000   | Fradrag EPS                | -3920   |
     | Folketrygd     | -128.592 | Uføre Ordinær sats  | -231.080 | SUM SU/år                  | =86.568 |
     | SUM            | =90.488  | SUM                 | =3920    | SUM SU/mnd                 | =7.214  |
     |                |          |                     |          | Utbetalt SU/år (avrundet)  | =86.568 |
     |                |          |                     |          | Utbetalt SU/mnd (avrundet) | =7.214  |
     +----------------+----------+---------------------+----------+----------------------------+---------+
     */
    @Test
    fun `beregningseksempel fra fagsiden`() {
        val periode = Periode.create(1.mai(2020), 30.april(2021))

        val arbeidsinntektPrÅr = 12000.0
        val folketrygdPrÅr = 128592.0
        val epsFolketrydPrÅr = 190000.0
        val epsAnnenNorskPrÅr = 45000.0

        val arbeidsinntektPrMnd = arbeidsinntektPrÅr / 12
        val folketrygdPrMnd = folketrygdPrÅr / 12
        val epsFolketrygdPrMnd = epsFolketrydPrÅr / 12
        val epsAnnenNorskPrMnd = epsAnnenNorskPrÅr / 12

        val uføreOrdinærSatsbeløp = 231080.0
        val beregningsgrunnlag = Beregningsgrunnlag.create(
            beregningsperiode = periode,
            forventetInntektPerÅr = 0.0,
            fradragFraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = arbeidsinntektPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.OffentligPensjon,
                    månedsbeløp = folketrygdPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                ),
                FradragFactory.ny(
                    type = Fradragstype.OffentligPensjon,
                    månedsbeløp = epsFolketrygdPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                ),
                FradragFactory.ny(
                    type = Fradragstype.PrivatPensjon,
                    månedsbeløp = epsAnnenNorskPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                )
            )
        )

        BeregningStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(beregningsgrunnlag).let {
            it.getSumYtelse() shouldBe 86568
            it.getSumFradrag() shouldBe (arbeidsinntektPrÅr + folketrygdPrÅr + (epsFolketrydPrÅr + epsAnnenNorskPrÅr - uføreOrdinærSatsbeløp))
                .plusOrMinus(0.5)
            it.getMånedsberegninger().forEach {
                it.getSumYtelse() shouldBe 7214
            }
        }
    }
}
