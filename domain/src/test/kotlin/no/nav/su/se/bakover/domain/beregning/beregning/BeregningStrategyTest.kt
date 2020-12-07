package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class BeregningStrategyTest {
    @Test
    fun `videresender korrekte verdier`() {
        val periode = Periode(
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.desember(2020)
        )
        val beregningsgrunnlag = Beregningsgrunnlag(
            beregningsperiode = periode,
            fradragFraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Kontantstøtte,
                    beløp = 1500.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            forventetInntektPrÅr = 12000.0
        )
        BeregningStrategy.BorAlene.beregn(beregningsgrunnlag).let {
            it.getPeriode().getFraOgMed() shouldBe periode.getFraOgMed()
            it.getPeriode().getTilOgMed() shouldBe periode.getTilOgMed()
            it.getSats() shouldBe Sats.HØY
            it.getFradrag() shouldBe beregningsgrunnlag.fradrag
            it.getMånedsberegninger() shouldHaveSize 12
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
