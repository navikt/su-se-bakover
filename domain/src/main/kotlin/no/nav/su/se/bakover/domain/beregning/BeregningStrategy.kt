package no.nav.su.se.bakover.domain.beregning

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow

class BeregningStrategyFactory {
    fun beregnUtenUtgangspunkt(
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger,
        beregningsPeriode: Periode,
        begrunnelse: String?,
    ): Beregning {
        val bosituasjon = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.singleFullstendigOrThrow()

        val beregningsgrunnlag = Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsPeriode,
            uføregrunnlag = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.uføre.grunnlag,
            fradragFraSaksbehandler = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag,
        ).getOrHandle {
            // TODO jah: Kan vurdere å legge på en left her (KanIkkeBeregne.UgyldigBeregningsgrunnlag
            throw IllegalArgumentException(it.toString())
        }
        val strategy =
            when (bosituasjon) {
                is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> BeregningStrategy.BorMedVoksne
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> BeregningStrategy.Eps67EllerEldre
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> BeregningStrategy.EpsUnder67År
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> BeregningStrategy.EpsUnder67ÅrOgUførFlyktning
                is Grunnlag.Bosituasjon.Fullstendig.Enslig -> BeregningStrategy.BorAlene
            }
        return strategy.beregnUtenUtgangspunkt(beregningsgrunnlag, begrunnelse)
    }

    fun beregnMedUtgangspunktIMånedsberegning(
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger,
        beregningsPeriode: Periode,
        begrunnelse: String?,
        månedsberegning: Månedsberegning,
    ): Beregning {
        val bosituasjon = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.singleFullstendigOrThrow()

        val beregningsgrunnlag = Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsPeriode,
            uføregrunnlag = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.uføre.grunnlag,
            fradragFraSaksbehandler = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag,
        ).getOrHandle {
            // TODO jah: Kan vurdere å legge på en left her (KanIkkeBeregne.UgyldigBeregningsgrunnlag
            throw IllegalArgumentException(it.toString())
        }
        val strategy =
            when (bosituasjon) {
                is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> BeregningStrategy.BorMedVoksne
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> BeregningStrategy.Eps67EllerEldre
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> BeregningStrategy.EpsUnder67År
                is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> BeregningStrategy.EpsUnder67ÅrOgUførFlyktning
                is Grunnlag.Bosituasjon.Fullstendig.Enslig -> BeregningStrategy.BorAlene
            }
        return strategy.beregnMedUtgangspunktIMånedsberegning(beregningsgrunnlag, begrunnelse, månedsberegning)
    }
}

sealed class BeregningStrategy {
    abstract fun fradragStrategy(): FradragStrategy
    abstract fun sats(): Sats
    abstract fun satsgrunn(): Satsgrunn

    fun beregnUtenUtgangspunkt(
        beregningsgrunnlag: Beregningsgrunnlag,
        begrunnelse: String? = null,
    ): Beregning {
        return BeregningFactory.ny(
            periode = beregningsgrunnlag.beregningsperiode,
            sats = sats(),
            fradrag = beregningsgrunnlag.fradrag,
            fradragStrategy = fradragStrategy(),
            begrunnelse = begrunnelse,
            utgangspunkt = null,
        )
    }

    fun beregnMedUtgangspunktIMånedsberegning(
        beregningsgrunnlag: Beregningsgrunnlag,
        begrunnelse: String? = null,
        utgangspunkt: Månedsberegning,
    ): Beregning {
        return BeregningFactory.ny(
            periode = beregningsgrunnlag.beregningsperiode,
            sats = sats(),
            fradrag = beregningsgrunnlag.fradrag,
            fradragStrategy = fradragStrategy(),
            begrunnelse = begrunnelse,
            utgangspunkt = utgangspunkt,
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

fun Grunnlag.Bosituasjon.Fullstendig.utledBeregningsstrategi(): BeregningStrategy {
    return when (this) {
        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> BeregningStrategy.BorMedVoksne
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> BeregningStrategy.EpsUnder67År
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> BeregningStrategy.Eps67EllerEldre
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> BeregningStrategy.EpsUnder67ÅrOgUførFlyktning
        is Grunnlag.Bosituasjon.Fullstendig.Enslig -> BeregningStrategy.BorAlene
    }
}
