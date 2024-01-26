package no.nav.su.se.bakover.domain.beregning

import arrow.core.getOrElse
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import satser.domain.SatsFactory
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
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

        val delperioder = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.map {
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
                    uføregrunnlag = when (
                        val vilkårsvurderinger =
                            grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
                    ) {
                        is Vilkårsvurderinger.Revurdering.Uføre -> {
                            vilkårsvurderinger.uføre.grunnlag
                        }

                        is Vilkårsvurderinger.Søknadsbehandling.Uføre -> {
                            vilkårsvurderinger.uføre.grunnlag
                        }

                        is Vilkårsvurderinger.Revurdering.Alder -> {
                            throw IllegalStateException("Uføresak med vilkårsvurderinger for alder!")
                        }

                        is Vilkårsvurderinger.Søknadsbehandling.Alder -> {
                            throw IllegalStateException("Uføresak med vilkårsvurderinger for alder!")
                        }
                    },
                    fradragFraSaksbehandler = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag,
                ).getOrElse {
                    // TODO jah: Kan vurdere å legge på en left her (KanIkkeBeregne.UgyldigBeregningsgrunnlag
                    throw IllegalArgumentException(it.toString())
                }.fradrag
            }
        }

        require(totalBeregningsperiode.fullstendigOverlapp(delperioder.map { it.periode() }))

        return BeregningFactory(clock).ny(
            fradrag = fradrag,
            begrunnelse = begrunnelse,
            beregningsperioder = delperioder,
        )
    }
}
