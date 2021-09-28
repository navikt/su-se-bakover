package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import org.junit.jupiter.api.Test

internal class BeregningStrategyTest {
    @Test
    fun `videresender korrekte verdier`() {
        val periode = Periode.create(
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.desember(2020)
        )
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
        BeregningStrategy.BorAlene.beregnSøknadsbehandling(beregningsgrunnlag, "en begrunnelse").let {
            it.periode.fraOgMed shouldBe periode.fraOgMed
            it.periode.tilOgMed shouldBe periode.tilOgMed
            it.getSats() shouldBe Sats.HØY
            it.getFradrag() shouldBe beregningsgrunnlag.fradrag
            it.getMånedsberegninger() shouldHaveSize 12
            it.getBegrunnelse() shouldBe "en begrunnelse"
        }
    }

    @Test
    fun `bor alene inneholder korrekte verdier`() {
        BeregningStrategy.BorAlene.sats() shouldBe Sats.HØY
        BeregningStrategy.BorAlene.fradragStrategy() shouldBe FradragStrategy.Enslig
    }

    @Test
    fun `bor med voksne inneholder korrekte verdier`() {
        BeregningStrategy.BorMedVoksne.sats() shouldBe Sats.ORDINÆR
        BeregningStrategy.BorMedVoksne.fradragStrategy() shouldBe FradragStrategy.Enslig
    }

    @Test
    fun `eps over 67 år inneholder korrekte verdier`() {
        BeregningStrategy.Eps67EllerEldre.sats() shouldBe Sats.ORDINÆR
        BeregningStrategy.Eps67EllerEldre.fradragStrategy() shouldBe FradragStrategy.EpsOver67År
    }

    @Test
    fun `eps under 67 år og ufør flyktning inneholder korrekte verdier`() {
        BeregningStrategy.EpsUnder67ÅrOgUførFlyktning.sats() shouldBe Sats.ORDINÆR
        BeregningStrategy.EpsUnder67ÅrOgUførFlyktning.fradragStrategy() shouldBe FradragStrategy.EpsUnder67ÅrOgUførFlyktning
    }

    @Test
    fun `eps under 67 år inneholder korrekte verdier`() {
        BeregningStrategy.EpsUnder67År.sats() shouldBe Sats.HØY
        BeregningStrategy.EpsUnder67År.fradragStrategy() shouldBe FradragStrategy.EpsUnder67År
    }
}
