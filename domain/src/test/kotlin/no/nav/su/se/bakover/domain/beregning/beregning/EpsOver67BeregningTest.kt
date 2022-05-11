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

internal class EpsOver67BeregningTest {

    /**
     * Eksempel fra 01.05.2020
     +----------------+----------+-------------+----------+----------------------------+------------+
     |     Bruker     |          |     EPS     |          |           Total            |            |
     +----------------+----------+-------------+----------+----------------------------+------------+
     | Stønadssats    | 231.080  | Folketrygd  |  190.000 | SUM Bruker                 | 39.999     |
     | Arbeidsinntekt | -71.081  | Garantipen. | -177.724 | Fradrag EPS                | -12.276    |
     | Folketrygd     | -120.000 | SUM         |   12.276 | SUM SU/år                  | =27.723    |
     | SUM            | =39.999  |             |          | SUM SU/mnd                 | =2.310,25 |
     |                |          |             |          | Utbetalt SU/år (avrundet)  | =27.720    |
     |                |          |             |          | Utbetalt SU/mnd (avrundet) | =2.310     |
     +----------------+----------+-------------+----------+----------------------------+------------+
     */
    @Test
    fun `beregningseksempel fra fagsiden`() {
        val periode = Periode.create(1.mai(2020), 30.april(2021))

        val arbeidsinntektPrÅr = 71081.0
        val folketrygdPrÅr = 120000.0
        val epsFolketrygdPrÅr = 190000.0

        val arbeidsinntektPrMnd = arbeidsinntektPrÅr / 12
        val folketrygdPrMnd = folketrygdPrÅr / 12
        val epsFolketrygdPrMnd = epsFolketrygdPrÅr / 12

        val garantipensjon = 177724.0
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
            )
        )

        BeregningFactory(fixedClock).ny(
            fradrag = beregningsgrunnlag.fradrag,
            begrunnelse = null,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = beregningsgrunnlag.beregningsperiode,
                    strategy = BeregningStrategy.Eps67EllerEldre(satsFactoryTest),
                )
            ),
        ).let {
            it.getSumYtelse() shouldBe 27720
            it.getSumFradrag() shouldBe (arbeidsinntektPrÅr + folketrygdPrÅr + (epsFolketrygdPrÅr - garantipensjon)).plusOrMinus(0.5)
            it.getMånedsberegninger().forEach {
                it.getSumYtelse() shouldBe 2310
            }
        }
    }
}
