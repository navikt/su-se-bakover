package no.nav.su.se.bakover.domain.beregning.beregning

import arrow.core.nonEmptyListOf
import beregning.domain.fradrag.FradragTilhører
import beregning.domain.fradrag.Fradragstype
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.test.arbeidsinntekt
import no.nav.su.se.bakover.test.bosituasjonEpsOver67
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingAlder
import org.junit.jupiter.api.Test
import satser.domain.Satskategori
import java.time.LocalDate

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
                    tilhører = FradragTilhører.BRUKER,
                ),
                lagFradragsgrunnlag(
                    type = Fradragstype.OffentligPensjon,
                    månedsbeløp = folketrygdPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                lagFradragsgrunnlag(
                    type = Fradragstype.OffentligPensjon,
                    månedsbeløp = epsFolketrygdPrMnd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
        )

        BeregningFactory(fixedClock).ny(
            fradrag = beregningsgrunnlag.fradrag,
            begrunnelse = null,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = beregningsgrunnlag.beregningsperiode,
                    strategy = BeregningStrategy.Eps67EllerEldre(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        ).let {
            it.getSumYtelse() shouldBe 27720
            it.getSumFradrag() shouldBe (arbeidsinntektPrÅr + folketrygdPrÅr + (epsFolketrygdPrÅr - garantipensjon)).plusOrMinus(
                0.5,
            )
            it.getMånedsberegninger().forEach {
                it.getSumYtelse() shouldBe 2310
            }
        }
    }

    @Test
    fun alder() {
        val bosituasjon = bosituasjonEpsOver67(
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
                    type = Fradragstype.OffentligPensjon,
                    månedsbeløp = 16000.33,
                    periode = stønadsperiode2021.periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
        ).also { (_, vilkårsvurdert) ->
            (vilkårsvurdert as VilkårsvurdertSøknadsbehandling.Innvilget).beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(LocalDate.now(1.juni(2021).fixedClock())),
                nySaksbehandler = saksbehandler,
            ).getOrFail().also { beregnet ->
                beregnet.beregning.getSumYtelse() shouldBe 116144
                beregnet.beregning.getSumFradrag() shouldBe 67927.95999999999
                beregnet.beregning.getMånedsberegninger().first().also {
                    it.getSumYtelse() shouldBe 8620
                    it.getSumFradrag() shouldBe 6189.996666666666
                    it.getSats() shouldBe Satskategori.ORDINÆR
                    it.getSatsbeløp() shouldBe 14810.333333333334
                }
                beregnet.beregning.getMånedsberegninger().last().also {
                    it.getSumYtelse() shouldBe 10208
                    it.getSumFradrag() shouldBe 5395.996666666666
                    it.getSats() shouldBe Satskategori.ORDINÆR
                    it.getSatsbeløp() shouldBe 15604.333333333334
                }
                beregnet.beregning.getMånedsberegninger().map { it.erFradragForEpsBenyttetIBeregning() }.all { it }
            }
        }
    }
}
