package no.nav.su.se.bakover.domain.beregning

import arrow.core.getOrHandle
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling

class BeregningStrategyFactory {
    fun beregn(
        søknadsbehandling: Søknadsbehandling,
        fradrag: List<Fradrag>,
        begrunnelse: String?,
    ): Beregning {
        if (søknadsbehandling.grunnlagsdata.bosituasjon.size != 1) throw IllegalStateException("Støtter ikke beregning av ingen eller flere bosituasjonsperioder")
        val bosituasjon = søknadsbehandling.grunnlagsdata.bosituasjon.first()

        val beregningsgrunnlag = Beregningsgrunnlag.tryCreate(
            beregningsperiode = søknadsbehandling.periode,
            uføregrunnlag = søknadsbehandling.grunnlagsdata.uføregrunnlag,
            fradragFraSaksbehandler = fradrag,
        ).getOrHandle {
            // TODO jah: Kan vurdere å legge på en left her (KanIkkeBeregne.UgyldigBeregningsgrunnlag
            throw IllegalArgumentException(it.toString())
        }
        val strategy =
            when (bosituasjon) {
                is Grunnlag.Bosituasjon.DelerBoligMedVoksneBarnEllerAnnenVoksen -> BeregningStrategy.BorMedVoksne
                is Grunnlag.Bosituasjon.EktefellePartnerSamboer.SektiSyvEllerEldre -> BeregningStrategy.Eps67EllerEldre
                is Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> BeregningStrategy.EpsUnder67År
                is Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.UførFlyktning -> BeregningStrategy.EpsUnder67ÅrOgUførFlyktning
                is Grunnlag.Bosituasjon.Enslig -> BeregningStrategy.BorAlene
                is Grunnlag.Bosituasjon.HarIkkeEPS -> throw IllegalStateException("Kan ikke beregne når man ikke har valgt om eps er ufør flyktning eller ikke")
                is Grunnlag.Bosituasjon.EktefellePartnerSamboer.Under67.IkkeBestemt -> throw IllegalStateException("Kan ikke beregne når man ikke har valgt om man bor alene eller med andre voksne")
            }
        // TODO jah: Kan vurdere å legge på en left her (KanIkkeBeregne.UfullstendigBehandlingsinformasjon
        return strategy.beregn(beregningsgrunnlag, begrunnelse)
    }
}

internal sealed class BeregningStrategy {
    abstract fun fradragStrategy(): FradragStrategy
    abstract fun sats(): Sats
    abstract fun satsgrunn(): Satsgrunn
    fun beregn(beregningsgrunnlag: Beregningsgrunnlag, begrunnelse: String? = null): Beregning {
        return BeregningFactory.ny(
            periode = beregningsgrunnlag.beregningsperiode,
            sats = sats(),
            fradrag = beregningsgrunnlag.fradrag,
            fradragStrategy = fradragStrategy(),
            begrunnelse = begrunnelse,
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
