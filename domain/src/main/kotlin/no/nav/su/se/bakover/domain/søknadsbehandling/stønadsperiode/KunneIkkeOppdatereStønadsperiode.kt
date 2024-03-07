package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import vilkår.vurderinger.domain.KunneIkkeLageGrunnlagsdata
import kotlin.reflect.KClass

sealed interface KunneIkkeOppdatereStønadsperiode {
    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out VilkårsvurdertSøknadsbehandling> = VilkårsvurdertSøknadsbehandling::class,
    ) : KunneIkkeOppdatereStønadsperiode

    data class KunneIkkeOppdatereGrunnlagsdata(
        val feil: KunneIkkeLageGrunnlagsdata,
    ) : KunneIkkeOppdatereStønadsperiode
}
