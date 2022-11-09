package no.nav.su.se.bakover.domain.vilkår.oppmøte

import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import java.util.UUID

data class LeggTilPersonligOppmøteVilkårRequest(
    val behandlingId: UUID,
    val vilkår: PersonligOppmøteVilkår.Vurdert,
)

sealed interface KunneIkkeLeggeTilPersonligOppmøteVilkår {
    data class Søknadsbehandling(val feil: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår) :
        KunneIkkeLeggeTilPersonligOppmøteVilkår

    data class Revurdering(val feil: no.nav.su.se.bakover.domain.revurdering.Revurdering.KunneIkkeLeggeTilPersonligOppmøteVilkår) :
        KunneIkkeLeggeTilPersonligOppmøteVilkår

    object FantIkkeBehandling : KunneIkkeLeggeTilPersonligOppmøteVilkår
}
