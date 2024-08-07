package no.nav.su.se.bakover.domain.beregning.beregning

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
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingAlder
import org.junit.jupiter.api.Test
import satser.domain.Satskategori
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag
import java.time.LocalDate

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
        val periode = Periode.create(1.mai(2020), 30.april(2021))

        val arbeidsinntektPrÅr = 180000.0
        val folketrygdPrÅr = 66323.0
        val arbeidsinntektPrMnd = arbeidsinntektPrÅr / 12
        val folketrygdPrMnd = folketrygdPrÅr / 12

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
            ),
        )

        BeregningFactory(fixedClock).ny(
            fradrag = beregningsgrunnlag.fradrag,
            begrunnelse = null,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = beregningsgrunnlag.beregningsperiode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        ).let {
            it.getSumYtelse() shouldBe 5028
            it.getSumFradrag() shouldBe (arbeidsinntektPrÅr + folketrygdPrÅr).plusOrMinus(0.5)
            it.getMånedsberegninger().forEach {
                it.getSumYtelse() shouldBe 419
            }
            it.getBegrunnelse() shouldBe null
        }
    }

    @Test
    fun alder() {
        vilkårsvurdertSøknadsbehandlingAlder(
            clock = fixedClock,
            stønadsperiode = stønadsperiode2021,
            customGrunnlag = listOf(
                arbeidsinntekt(
                    periode = stønadsperiode2021.periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).also { (_, søknadsbehandling) ->
            (søknadsbehandling as VilkårsvurdertSøknadsbehandling.Innvilget).beregn(
                begrunnelse = "blabla",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(LocalDate.now(1.juni(2021).fixedClock())),
                nySaksbehandler = saksbehandler,
            ).getOrFail().also {
                it.beregning.getSumYtelse() shouldBe 138992
                it.beregning.getSumFradrag() shouldBe 60000
                it.beregning.getMånedsberegninger().first().also {
                    it.getSumYtelse() shouldBe 11010
                    it.getSumFradrag() shouldBe 5000
                    it.getSats() shouldBe Satskategori.HØY
                    it.getSatsbeløp() shouldBe 16010.416666666666
                }
                it.beregning.getMånedsberegninger().last().also {
                    it.getSumYtelse() shouldBe 11869
                    it.getSumFradrag() shouldBe 5000
                    it.getSats() shouldBe Satskategori.HØY
                    it.getSatsbeløp() shouldBe 16868.75
                }
            }
        }
    }
}
