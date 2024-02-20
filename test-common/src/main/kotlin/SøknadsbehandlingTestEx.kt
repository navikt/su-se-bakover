package no.nav.su.se.bakover.test

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import vilkår.vurderinger.domain.GrunnlagsdataOgVilkårsvurderinger

fun Søknadsbehandling.shouldBeEqualToExceptId(other: Søknadsbehandling) {
    this.shouldBeEqualToIgnoringFields(
        other,
        Søknadsbehandling::id,
        Søknadsbehandling::grunnlagsdataOgVilkårsvurderinger,
        GrunnlagsdataOgVilkårsvurderinger::grunnlagsdata,
        GrunnlagsdataOgVilkårsvurderinger::vilkårsvurderinger,
        GrunnlagsdataOgVilkårsvurderinger::eksterneGrunnlag,
    )
    this.grunnlagsdataOgVilkårsvurderinger.shouldBeEqualToExceptId(other.grunnlagsdataOgVilkårsvurderinger)
}
