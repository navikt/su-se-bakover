package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.satsFactoryTest
import org.junit.jupiter.api.Test

internal class BeregningStrategyTest {
    @Test
    fun `videresender korrekte verdier`() {
        val periode = år(2020)
        val beregningsgrunnlag = Beregningsgrunnlag.create(
            beregningsperiode = periode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
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
                    strategy = BeregningStrategy.BorAlene(satsFactoryTest),
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

    @Test
    fun `bor alene inneholder korrekte verdier`() {
        BeregningStrategy.BorAlene(satsFactoryTest).fradragStrategy() shouldBe FradragStrategy.Enslig
        BeregningStrategy.BorAlene(satsFactoryTest).satsgrunn() shouldBe Satsgrunn.ENSLIG
    }

    @Test
    fun `bor med voksne inneholder korrekte verdier`() {
        BeregningStrategy.BorMedVoksne(satsFactoryTest).fradragStrategy() shouldBe FradragStrategy.Enslig
        BeregningStrategy.BorMedVoksne(satsFactoryTest)
            .satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
    }

    @Test
    fun `eps over 67 år inneholder korrekte verdier`() {
        BeregningStrategy.Eps67EllerEldre(satsFactoryTest).fradragStrategy() shouldBe FradragStrategy.EpsOver67År
        BeregningStrategy.Eps67EllerEldre(satsFactoryTest)
            .satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE
    }

    @Test
    fun `eps under 67 år og ufør flyktning inneholder korrekte verdier`() {
        BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactoryTest)
            .fradragStrategy() shouldBe FradragStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactoryTest)
        BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactoryTest)
            .satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
    }

    @Test
    fun `eps under 67 år inneholder korrekte verdier`() {
        BeregningStrategy.EpsUnder67År(satsFactoryTest).fradragStrategy() shouldBe FradragStrategy.EpsUnder67År
        BeregningStrategy.EpsUnder67År(satsFactoryTest)
            .satsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
    }
}
