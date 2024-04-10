package no.nav.su.se.bakover.domain.beregning.beregning

import arrow.core.nonEmptyListOf
import beregning.domain.BeregningFactory
import beregning.domain.BeregningStrategy
import beregning.domain.Beregningsgrunnlag
import beregning.domain.Beregningsperiode
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.fixedClock
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.test.arbeidsinntekt
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
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
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag
import java.time.LocalDate

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
                Uføregrunnlag(
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
                lagFradragsgrunnlag(
                    type = Fradragstype.PrivatPensjon,
                    månedsbeløp = epsAnnenNorskPrMnd,
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
                    strategy = BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
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

    @Test
    fun alder() {
        val bosituasjon = bosituasjongrunnlagEpsUførFlyktning(
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
                    månedsbeløp = 24000.33,
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
                beregnet.beregning.getSumYtelse() shouldBe 74828
                beregnet.beregning.getSumFradrag() shouldBe 109250.72000000004
                beregnet.beregning.getMånedsberegninger().first().also {
                    it.getSumYtelse() shouldBe 5067
                    it.getSumFradrag() shouldBe 9743.640000000003
                    it.getSats() shouldBe Satskategori.ORDINÆR
                    it.getSatsbeløp() shouldBe 14810.333333333334
                }
                beregnet.beregning.getMånedsberegninger().last().also {
                    it.getSumYtelse() shouldBe 6820
                    it.getSumFradrag() shouldBe 8784.52
                    it.getSats() shouldBe Satskategori.ORDINÆR
                    it.getSatsbeløp() shouldBe 15604.333333333334
                }
                beregnet.beregning.getMånedsberegninger().map { it.erFradragForEpsBenyttetIBeregning() }.all { it }
            }
        }
    }
}
