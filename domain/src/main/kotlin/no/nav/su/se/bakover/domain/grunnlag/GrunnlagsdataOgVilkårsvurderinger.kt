package no.nav.su.se.bakover.domain.grunnlag

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger

sealed class GrunnlagsdataOgVilkårsvurderinger {
    abstract val grunnlagsdata: Grunnlagsdata
    abstract val vilkårsvurderinger: Vilkårsvurderinger

    fun periode(): Periode? {
        kastHvisPerioderIkkeErLike()
        return grunnlagsdata.periode ?: vilkårsvurderinger.periode
    }

    fun erVurdert(): Boolean = vilkårsvurderinger.erVurdert && grunnlagsdata.erUtfylt

    protected fun kastHvisPerioderIkkeErLike() {
        if (grunnlagsdata.periode != null && vilkårsvurderinger.periode != null) {
            require(grunnlagsdata.periode == vilkårsvurderinger.periode) {
                "Grunnlagsdataperioden (${grunnlagsdata.periode}) må være lik vilkårsvurderingerperioden (${vilkårsvurderinger.periode})"
            }
        }
    }

    data class Søknadsbehandling(
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
    ) : GrunnlagsdataOgVilkårsvurderinger() {

        init {
            kastHvisPerioderIkkeErLike()
        }
    }

    data class Revurdering(
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
    ) : GrunnlagsdataOgVilkårsvurderinger() {

        init {
            kastHvisPerioderIkkeErLike()
        }
    }
}
