package beregning.domain

import arrow.core.getOrElse
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import satser.domain.SatsFactory
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.vurderinger.domain.GrunnlagsdataOgVilkårsvurderinger
import java.time.Clock

class BeregningStrategyFactory(
    val clock: Clock,
    val satsFactory: SatsFactory,
) {
    fun beregn(
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger,
        begrunnelse: String?,
        sakstype: Sakstype,
    ): Beregning {
        val totalBeregningsperiode = grunnlagsdataOgVilkårsvurderinger.periode()!!

        require(grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.isNotEmpty()) { "Bosituasjon er påkrevet for å kunne beregne." }

        val delperioder: List<Beregningsperiode> = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.map {
            Beregningsperiode(
                periode = it.periode,
                strategy = (it as Bosituasjon.Fullstendig).utledBeregningsstrategi(satsFactory, sakstype),
            )
        }

        val fradrag = when (sakstype) {
            Sakstype.ALDER -> {
                grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag
            }

            Sakstype.UFØRE -> {
                Beregningsgrunnlag.tryCreate(
                    beregningsperiode = totalBeregningsperiode,
                    uføregrunnlag = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.uføreVilkårKastHvisAlder().grunnlag,
                    fradragFraSaksbehandler = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag,
                ).getOrElse {
                    // TODO jah: Kan vurdere å legge på en left her (KanIkkeBeregne.UgyldigBeregningsgrunnlag
                    throw IllegalArgumentException(it.toString())
                }.fradrag
            }
        }

        require(totalBeregningsperiode.fullstendigOverlapp(delperioder.perioder()))

        return BeregningFactory(clock).ny(
            fradrag = fradrag,
            begrunnelse = begrunnelse,
            beregningsperioder = delperioder,
        )
    }
}
