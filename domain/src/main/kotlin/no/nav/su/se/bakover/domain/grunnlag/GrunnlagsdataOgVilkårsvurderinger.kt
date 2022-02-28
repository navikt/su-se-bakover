package no.nav.su.se.bakover.domain.grunnlag

import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger

sealed interface GrunnlagsdataOgVilkårsvurderinger {
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger

    data class Søknadsbehandling(
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
    ) : GrunnlagsdataOgVilkårsvurderinger {

        companion object {
            val IkkeVurdert = Søknadsbehandling(
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

    data class Revurdering(
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
    ) : GrunnlagsdataOgVilkårsvurderinger {

        companion object {
            val IkkeVurdert = Revurdering(
                Grunnlagsdata.IkkeVurdert,
                Vilkårsvurderinger.Revurdering.IkkeVurdert,
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
}
