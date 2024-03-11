package no.nav.su.se.bakover.domain.beregning.beregning

import beregning.domain.BeregningFactory
import beregning.domain.BeregningStrategy.BorAlene
import beregning.domain.BeregningStrategy.BorMedVoksne
import beregning.domain.BeregningStrategy.Eps67EllerEldre
import beregning.domain.BeregningStrategy.EpsUnder67År
import beregning.domain.BeregningStrategy.EpsUnder67ÅrOgUførFlyktning
import beregning.domain.Beregningsgrunnlag
import beregning.domain.Beregningsperiode
import beregning.domain.fradrag.FradragStrategy
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import satser.domain.Satsgrunn
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag

internal class BeregningStrategyTest {
    @Test
    fun `videresender korrekte verdier`() {
        val periode = år(2021)
        val beregningsgrunnlag = Beregningsgrunnlag.create(
            beregningsperiode = periode,
            uføregrunnlag = listOf(
                Uføregrunnlag(
                    periode = periode,
                    uføregrad = Uføregrad.parse(90),
                    forventetInntekt = 12000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Kontantstøtte,
                    månedsbeløp = 1500.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )
        BeregningFactory(fixedClock).ny(
            fradrag = beregningsgrunnlag.fradrag,
            begrunnelse = "en begrunnelse",
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = beregningsgrunnlag.beregningsperiode,
                    strategy = BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        ).let {
            it.periode.fraOgMed shouldBe periode.fraOgMed
            it.periode.tilOgMed shouldBe periode.tilOgMed
            it.getFradrag() shouldBe beregningsgrunnlag.fradrag
            it.getMånedsberegninger() shouldHaveSize 12
            it.getBegrunnelse() shouldBe "en begrunnelse"
        }
    }

    private val sakstypeUføre = Sakstype.UFØRE
    private val sakstypeAlder = Sakstype.ALDER
    private val satsFactory = satsFactoryTestPåDato()

    @Test
    fun `bor alene inneholder korrekte verdier`() {
        val borAleneUføre = BorAlene(satsFactory, sakstypeUføre)
        borAleneUføre.fradragStrategy() shouldBe FradragStrategy.Uføre.Enslig
        borAleneUføre.satsgrunn() shouldBe Satsgrunn.ENSLIG

        val borAleneAlder = BorAlene(satsFactory, sakstypeAlder)
        borAleneAlder.fradragStrategy() shouldBe FradragStrategy.Alder.Enslig
        borAleneAlder.satsgrunn() shouldBe Satsgrunn.ENSLIG
    }

    @Test
    fun `bor med voksne inneholder korrekte verdier`() {
        val borMedVoksneUføre = BorMedVoksne(satsFactory, sakstypeUføre)
        borMedVoksneUføre.fradragStrategy() shouldBe FradragStrategy.Uføre.Enslig
        borMedVoksneUføre.satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN

        val borMedVoksneAlder = BorMedVoksne(satsFactoryTestPåDato(), sakstypeAlder)
        borMedVoksneAlder.fradragStrategy() shouldBe FradragStrategy.Alder.Enslig
        borMedVoksneAlder.satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
    }

    @Test
    fun `eps over 67 år inneholder korrekte verdier`() {
        val eps67EllerEldreUføre = Eps67EllerEldre(satsFactory, sakstypeUføre)
        eps67EllerEldreUføre.fradragStrategy() shouldBe FradragStrategy.Uføre.EpsOver67År(satsFactory)
        eps67EllerEldreUføre.satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE

        val eps67EllerEldreAlder = Eps67EllerEldre(satsFactory, sakstypeAlder)
        eps67EllerEldreAlder.fradragStrategy() shouldBe FradragStrategy.Alder.EpsOver67År(satsFactory)
        eps67EllerEldreAlder.satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE
    }

    @Test
    fun `eps under 67 år og ufør flyktning inneholder korrekte verdier`() {
        val epsUførFlyktningUføre = EpsUnder67ÅrOgUførFlyktning(satsFactory, sakstypeUføre)
        epsUførFlyktningUføre.fradragStrategy() shouldBe FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactory)
        epsUførFlyktningUføre.satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING

        val epsUførFlyktningAlder = EpsUnder67ÅrOgUførFlyktning(satsFactory, sakstypeAlder)
        epsUførFlyktningAlder.fradragStrategy() shouldBe FradragStrategy.Alder.EpsUnder67ÅrOgUførFlyktning(satsFactory)
        epsUførFlyktningAlder.satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
    }

    @Test
    fun `eps under 67 år inneholder korrekte verdier`() {
        val epsUnder67ÅrUføre = EpsUnder67År(satsFactory, sakstypeUføre)
        epsUnder67ÅrUføre.fradragStrategy() shouldBe FradragStrategy.Uføre.EpsUnder67År
        epsUnder67ÅrUføre.satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67

        val epsUnder67ÅrAlder = EpsUnder67År(satsFactory, sakstypeAlder)
        epsUnder67ÅrAlder.fradragStrategy() shouldBe FradragStrategy.Alder.EpsUnder67År
        epsUnder67ÅrAlder.satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
    }
}
