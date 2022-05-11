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
import no.nav.su.se.bakover.test.satsFactoryTest
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
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = arbeidsinntektPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                lagFradragsgrunnlag(
                    type = Fradragstype.OffentligPensjon,
                    månedsbeløp = folketrygdPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                lagFradragsgrunnlag(
                    type = Fradragstype.OffentligPensjon,
                    månedsbeløp = epsFolketrygdPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                ),
                lagFradragsgrunnlag(
                    type = Fradragstype.PrivatPensjon,
                    månedsbeløp = epsAnnenNorskPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
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
                    strategy = BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactoryTest),
                )
            ),
        ).let {
            it.getSumYtelse() shouldBe 86568
            it.getSumFradrag() shouldBe (arbeidsinntektPrÅr + folketrygdPrÅr + (epsFolketrydPrÅr + epsAnnenNorskPrÅr - uføreOrdinærSatsbeløp))
                .plusOrMinus(0.5)
            it.getMånedsberegninger().forEach {
                it.getSumYtelse() shouldBe 7214
            }
        }
    }
}
