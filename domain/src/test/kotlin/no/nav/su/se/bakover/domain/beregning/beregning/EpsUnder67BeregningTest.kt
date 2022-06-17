package no.nav.su.se.bakover.domain.beregning.beregning

import arrow.core.nonEmptyListOf
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.satser.Satskategori
import no.nav.su.se.bakover.test.arbeidsinntekt
import no.nav.su.se.bakover.test.bosituasjonEpsOver67
import no.nav.su.se.bakover.test.bosituasjonEpsUnder67
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingAlder
import org.junit.jupiter.api.Test
import java.time.LocalDate

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
                    strategy = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato(), Sakstype.UFØRE),
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

    @Test
    fun alder() {
        val bosituasjon = bosituasjonEpsUnder67(
            periode = stønadsperiode2021.periode,
        )

        vilkårsvurdertSøknadsbehandlingAlder(
            clock = fixedClock,
            stønadsperiode = stønadsperiode2021,
            customVilkår = listOf(
                formuevilkårMedEps0Innvilget(
                    periode = stønadsperiode2021.periode,
                    bosituasjon = nonEmptyListOf(bosituasjon),
                ),
            ),
            customGrunnlag = listOf(
                bosituasjon,

                arbeidsinntekt(
                    periode = stønadsperiode2021.periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 4000.33,
                    periode = stønadsperiode2021.periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
        ).also { (_, vilkårsvurdert) ->
            vilkårsvurdert.beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(LocalDate.now(1.juni(2021).fixedClock())),
            ).getOrFail().also { beregnet ->
                beregnet.beregning.getSumYtelse() shouldBe 90984
                beregnet.beregning.getSumFradrag() shouldBe 108003.96
                beregnet.beregning.getMånedsberegninger().first().also {
                    it.getSumYtelse() shouldBe 7010
                    it.getSumFradrag() shouldBe 9000.33
                    it.getSats() shouldBe Satskategori.HØY
                    it.getSatsbeløp() shouldBe 16010.416666666666
                }
                beregnet.beregning.getMånedsberegninger().last().also {
                    it.getSumYtelse() shouldBe 7868
                    it.getSumFradrag() shouldBe 9000.33
                    it.getSats() shouldBe Satskategori.HØY
                    it.getSatsbeløp() shouldBe 16868.75
                }
                beregnet.beregning.getMånedsberegninger().map { it.erFradragForEpsBenyttetIBeregning() }.all { it }
            }
        }
    }
}
