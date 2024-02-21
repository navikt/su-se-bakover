package behandling.revurdering.domain

import arrow.core.getOrElse
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.harFjernetEllerEndretEps
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.perioderMedEPS
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.perioderUtenEPS
import vilkår.common.domain.Vilkår
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.GrunnlagsdataOgVilkårsvurderinger
import vilkår.vurderinger.domain.StøtterIkkeHentingAvEksternGrunnlag
import vilkår.vurderinger.domain.Vilkårsvurderinger
import vilkår.vurderinger.domain.fjernFradragEPS
import vilkår.vurderinger.domain.kastHvisPerioderIkkeErLike

data class GrunnlagsdataOgVilkårsvurderingerRevurdering(
    override val grunnlagsdata: Grunnlagsdata,
    override val vilkårsvurderinger: VilkårsvurderingerRevurdering,
    override val eksterneGrunnlag: EksterneGrunnlag = StøtterIkkeHentingAvEksternGrunnlag,
) : GrunnlagsdataOgVilkårsvurderinger {
    init {
        kastHvisPerioderIkkeErLike()
    }

    /**
     * Fjerner EPS sin formue/fradrag dersom søker ikke har EPS.
     * */
    fun oppdaterBosituasjon(
        bosituasjon: List<Bosituasjon.Fullstendig>,
    ): GrunnlagsdataOgVilkårsvurderinger {
        val grunnlagsdataJustertForEPS = Grunnlagsdata.tryCreate(
            /*
             * Hvis vi går fra "eps" til "ingen eps" må vi fjerne fradragene for EPS for alle periodene
             * hvor det eksiterer fradrag for EPS. Ved endring fra "ingen eps" til "eps" er det umulig for
             * oss å vite om det skal eksistere fradrag, caset er derfor uhåndtert (opp til saksbehandler).
             */
            grunnlagsdata.fradragsgrunnlag.fjernFradragEPS(bosituasjon.perioderUtenEPS()),
            bosituasjon,
        ).getOrElse {
            throw IllegalStateException(it.toString())
        }

        val formueJustertForEPS = vilkårsvurderinger.formue
            /*
             * Hvis vi går fra "ingen eps" til "eps" må vi fylle på med tomme verdier for EPS formue for
             * periodene hvor vi tidligere ikke hadde eps.
             */
            .leggTilTomEPSFormueHvisDetMangler(bosituasjon.perioderMedEPS())
            /*
             * Hvis vi går fra "eps" til "ingen eps" må vi fjerne formue for alle periodene hvor vi
             * ikke lenger har eps.
             */
            .fjernEPSFormue(bosituasjon.perioderUtenEPS())
            .slåSammenLikePerioder()

        return GrunnlagsdataOgVilkårsvurderingerRevurdering(
            grunnlagsdata = grunnlagsdataJustertForEPS,
            vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(formueJustertForEPS),
            eksterneGrunnlag = if (this.grunnlagsdata.bosituasjon.harFjernetEllerEndretEps(bosituasjon)) {
                eksterneGrunnlag.fjernEps()
            } else {
                eksterneGrunnlag
            },
        )
    }

    override fun oppdaterVilkår(vilkår: Vilkår): GrunnlagsdataOgVilkårsvurderingerRevurdering {
        return copy(vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(vilkår))
    }

    override fun oppdaterGrunnlagsdata(grunnlagsdata: Grunnlagsdata): GrunnlagsdataOgVilkårsvurderingerRevurdering {
        return copy(grunnlagsdata = grunnlagsdata)
    }

    override fun oppdaterVilkårsvurderinger(vilkårsvurderinger: Vilkårsvurderinger): GrunnlagsdataOgVilkårsvurderingerRevurdering {
        // TODO jah: Dersom vi skal slippe cast, må vi utvide GrunnlagsdataOgVilkårsvurderinger med en generisk type for vilkårsvurderinger
        return copy(vilkårsvurderinger = vilkårsvurderinger as VilkårsvurderingerRevurdering)
    }

    override fun oppdaterFradragsgrunnlag(fradragsgrunnlag: List<Fradragsgrunnlag>): GrunnlagsdataOgVilkårsvurderingerRevurdering {
        return copy(grunnlagsdata = grunnlagsdata.copy(fradragsgrunnlag = fradragsgrunnlag))
    }

    /* jah: Beholdes så lenge command-typene støtter fradragstypen avkorting. En mulighet er å splitte fradragstypene i command/query slik at vi ikke trenger bekymre oss for at ugyldige fradrag sniker seg inn i beregningen. */
    override fun harAvkortingsfradrag(): Boolean {
        return grunnlagsdata.fradragsgrunnlag.any { it.fradrag.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
    }

    override fun oppdaterEksterneGrunnlag(eksternGrunnlag: EksterneGrunnlag): GrunnlagsdataOgVilkårsvurderingerRevurdering {
        throw UnsupportedOperationException("Støtter ikke å legge til eksterne grunnlag for revurdering")
    }

    override fun oppdaterOpplysningsplikt(opplysningspliktVilkår: OpplysningspliktVilkår): GrunnlagsdataOgVilkårsvurderingerRevurdering {
        return this.copy(
            vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(
                vilkår = opplysningspliktVilkår,
            ),
        )
    }

    override fun copyWithNewIds(): GrunnlagsdataOgVilkårsvurderingerRevurdering = this.copy(
        grunnlagsdata = grunnlagsdata.copyWithNewIds(),
        vilkårsvurderinger = vilkårsvurderinger.copyWithNewIds() as VilkårsvurderingerRevurdering,
        eksterneGrunnlag = eksterneGrunnlag.copyWithNewIds(),
    )
}
