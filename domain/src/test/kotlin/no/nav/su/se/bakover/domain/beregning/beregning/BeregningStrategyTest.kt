package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class BeregningStrategyTest {
    @Test
    fun `ideresender korrekte verdier`() {
        val periode = Periode(
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.januar(2020)
        )
        val beregningsgrunnlag = Beregningsgrunnlag(
            fraOgMed = periode.fraOgMed(),
            tilOgMed = periode.tilOgMed(),
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Kontantstøtte,
                    beløp = 1500.0,
                    utenlandskInntekt = null,
                    periode = periode
                )
            ),
            forventetInntekt = 0
        )
        BeregningStrategy.BorAlene.beregn(beregningsgrunnlag).let {
            it.periode().fraOgMed() shouldBe beregningsgrunnlag.fraOgMed
            it.periode().tilOgMed() shouldBe beregningsgrunnlag.tilOgMed
            it.getSats() shouldBe Sats.HØY
            it.getFradrag() shouldBe beregningsgrunnlag.fradrag
            it.getMånedsberegninger() shouldHaveSize 1
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
        BeregningStrategy.EpsOver67År.sats() shouldBe Sats.ORDINÆR
        BeregningStrategy.EpsOver67År.fradragStrategy() shouldBe FradragStrategy.EpsOver67År
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
