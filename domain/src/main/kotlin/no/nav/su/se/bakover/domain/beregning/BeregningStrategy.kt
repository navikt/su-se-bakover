package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling

class BeregningStrategyFactory {
    fun beregn(søknadsbehandling: Søknadsbehandling, fradrag: List<Fradrag>): Beregning {
        val beregningsgrunnlag = Beregningsgrunnlag.create(
            beregningsperiode = søknadsbehandling.periode,
            forventetInntektPerÅr = søknadsbehandling.behandlingsinformasjon.uførhet?.forventetInntekt?.toDouble()
                ?: 0.0,
            fradragFraSaksbehandler = fradrag
        )
        val strategy = søknadsbehandling.behandlingsinformasjon.getBeregningStrategy()
        // TODO jah: Kan vurdere å legge på en left her (KanIkkeBeregne.UfullstendigBehandlingsinformasjon
        return strategy.orNull()!!.beregn(beregningsgrunnlag)
    }
}

internal sealed class BeregningStrategy {
    abstract fun fradragStrategy(): FradragStrategy
    abstract fun sats(): Sats
    abstract fun satsgrunn(): Satsgrunn
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
        override fun satsgrunn(): Satsgrunn = Satsgrunn.ENSLIG
    }

    object BorMedVoksne : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.Enslig
        override fun sats(): Sats = Sats.ORDINÆR
        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
    }

    object Eps67EllerEldre : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.EpsOver67År
        override fun sats(): Sats = Sats.ORDINÆR
        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE
    }

    object EpsUnder67ÅrOgUførFlyktning : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.EpsUnder67ÅrOgUførFlyktning
        override fun sats(): Sats = Sats.ORDINÆR
        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
    }

    object EpsUnder67År : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.EpsUnder67År
        override fun sats(): Sats = Sats.HØY
        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
    }
}
