package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy

internal sealed class BeregningStrategy {
    abstract fun fradragStrategy(): FradragStrategy
    abstract fun sats(): Sats
    fun beregn(beregningsgrunnlag: Beregningsgrunnlag): Beregning {
        val periode = Periode(
            beregningsgrunnlag.fraOgMed,
            beregningsgrunnlag.tilOgMed
        )
        return BeregningFactory.ny(
            periode = periode,
            sats = sats(),
            fradrag = fradragStrategy().beregnFradrag(
                beregningsgrunnlag.forventetInntekt,
                beregningsgrunnlag.fradrag,
                periode
            )
        )
    }

    object BorAlene : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.Enslig
        override fun sats(): Sats = Sats.HØY
    }

    object BorMedVoksne : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.Enslig
        override fun sats(): Sats = Sats.ORDINÆR
    }

    // Alle som er over 67, skal få denne begrunnelse, selv om man i praksis kan si EPS over 67 + mottar SU
    // i frontend
    object EpsOver67År : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.EpsOver67År
        override fun sats(): Sats = Sats.ORDINÆR
    }

    object EpsUnder67ÅrOgUførFlyktning : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.EpsUnder67ÅrOgUførFlyktning
        override fun sats(): Sats = Sats.ORDINÆR
    }

    object EpsUnder67År : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.EpsUnder67År
        override fun sats(): Sats = Sats.HØY
    }
}
