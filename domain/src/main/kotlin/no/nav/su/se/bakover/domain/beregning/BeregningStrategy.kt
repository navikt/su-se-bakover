package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling

class BeregningStrategyFactory() {
    fun beregn(søknadsbehandling: Søknadsbehandling, periode: Periode, fradrag: List<Fradrag>): Beregning {
        val beregningsgrunnlag = Beregningsgrunnlag.create(
            beregningsperiode = Periode.create(periode.getFraOgMed(), periode.getTilOgMed()),
            forventetInntektPerÅr = søknadsbehandling.behandlingsinformasjon.uførhet?.forventetInntekt?.toDouble()
                ?: 0.0,
            fradragFraSaksbehandler = fradrag
        )
        val strategy = søknadsbehandling.behandlingsinformasjon.bosituasjon!!.getBeregningStrategy()
        return strategy.beregn(beregningsgrunnlag)
    }
}

internal sealed class BeregningStrategy {
    abstract fun fradragStrategy(): FradragStrategy
    abstract fun sats(): Sats
    fun beregn(beregningsgrunnlag: Beregningsgrunnlag): Beregning {
        return BeregningFactory.ny(
            periode = beregningsgrunnlag.beregningsperiode,
            sats = sats(),
            fradrag = beregningsgrunnlag.fradrag,
            fradragStrategy = fradragStrategy()
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
    object Eps67EllerEldre : BeregningStrategy() {
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
