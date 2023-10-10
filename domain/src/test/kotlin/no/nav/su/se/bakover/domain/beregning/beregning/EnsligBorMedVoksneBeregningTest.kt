package no.nav.su.se.bakover.domain.beregning.beregning

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
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.satser.Satskategori
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.test.arbeidsinntekt
import no.nav.su.se.bakover.test.bosituasjonBorMedAndreVoksne
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingAlder
import org.junit.jupiter.api.Test
import java.time.LocalDate

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
        val periode = Periode.create(1.mai(2020), 30.april(2021))

        val arbeidsinntektPrÅr = 20000.0
        val folketrygdPrÅr = 14256.0
        val utenlandskInntektPrÅr = 40927.0

        val arbeidsinntektPrMnd = arbeidsinntektPrÅr / 12
        val folketrygdPrMnd = folketrygdPrÅr / 12
        val utenlandskInntektPrMnd = utenlandskInntektPrÅr / 12

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
                    månedsbeløp = utenlandskInntektPrMnd,
                    periode = periode,
                    utenlandskInntekt = UtenlandskInntekt.create(
                        beløpIUtenlandskValuta = 10,
                        valuta = "Andebydollars",
                        kurs = 3.0,
                    ),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        BeregningFactory(fixedClock).ny(
            fradrag = beregningsgrunnlag.fradrag,
            begrunnelse = "bor med voksen",
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = beregningsgrunnlag.beregningsperiode,
                    strategy = BeregningStrategy.BorMedVoksne(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        ).let {
            it.getSumYtelse() shouldBe 155892
            it.getSumFradrag() shouldBe (arbeidsinntektPrÅr + folketrygdPrÅr + utenlandskInntektPrÅr).plusOrMinus(0.5)
            it.getMånedsberegninger().forEach {
                it.getSumYtelse() shouldBe 12991
            }
            it.getBegrunnelse() shouldBe "bor med voksen"
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
                bosituasjonBorMedAndreVoksne(
                    periode = stønadsperiode2021.periode,
                ),
            ),
        ).also { (sak, vilkårsvurdert) ->
            (vilkårsvurdert as VilkårsvurdertSøknadsbehandling.Innvilget).beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(LocalDate.now(1.juni(2021).fixedClock())),
                nySaksbehandler = saksbehandler,
                uteståendeAvkortingPåSak = sak.uteståendeAvkortingSkalAvkortes,
            ).getOrFail().also {
                it.beregning.getSumYtelse() shouldBe 124072
                it.beregning.getSumFradrag() shouldBe 60000
                it.beregning.getMånedsberegninger().first().also {
                    it.getSumYtelse() shouldBe 9810
                    it.getSumFradrag() shouldBe 5000
                    it.getSats() shouldBe Satskategori.ORDINÆR
                    it.getSatsbeløp() shouldBe 14810.333333333334
                }
                it.beregning.getMånedsberegninger().last().also {
                    it.getSumYtelse() shouldBe 10604
                    it.getSumFradrag() shouldBe 5000
                    it.getSats() shouldBe Satskategori.ORDINÆR
                    it.getSatsbeløp() shouldBe 15604.333333333334
                }
            }
        }
    }
}
