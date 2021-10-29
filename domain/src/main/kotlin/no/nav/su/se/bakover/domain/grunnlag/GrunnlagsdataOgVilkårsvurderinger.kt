package no.nav.su.se.bakover.domain.grunnlag

import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger

data class GrunnlagsdataOgVilkårsvurderinger(
    val grunnlagsdata: Grunnlagsdata,
    val vilkårsvurderinger: Vilkårsvurderinger,
) {
    companion object {
        val IkkeVurdert = GrunnlagsdataOgVilkårsvurderinger(
            Grunnlagsdata.IkkeVurdert,
            Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert,
        )
    }

    init {
        if (grunnlagsdata.periode != null && vilkårsvurderinger.periode != null) {
            require(grunnlagsdata.periode == vilkårsvurderinger.periode) {
                "Grunnlagsdataperioden (${grunnlagsdata.periode}) må være lik vilkårsvurderingerperioden (${vilkårsvurderinger.periode})"
            }
        }
    }
}
