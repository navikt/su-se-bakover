package no.nav.su.se.bakover.domain.vilkår.oppmøte

import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import java.util.UUID

data class LeggTilPersonligOppmøteVilkårRequest(
    val behandlingId: UUID,
    val vilkår: PersonligOppmøteVilkår.Vurdert,
)

sealed interface KunneIkkeLeggeTilPersonligOppmøteVilkårForSøknadsbehandling {
    data class Underliggende(
        val feil: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår,
    ) : KunneIkkeLeggeTilPersonligOppmøteVilkårForSøknadsbehandling

    data object FantIkkeBehandling : KunneIkkeLeggeTilPersonligOppmøteVilkårForSøknadsbehandling
}

sealed interface KunneIkkeLeggeTilPersonligOppmøteVilkårForRevurdering {

    data class Underliggende(
        val feil: no.nav.su.se.bakover.domain.revurdering.Revurdering.KunneIkkeLeggeTilPersonligOppmøteVilkår,
    ) : KunneIkkeLeggeTilPersonligOppmøteVilkårForRevurdering

    data object FantIkkeBehandling : KunneIkkeLeggeTilPersonligOppmøteVilkårForRevurdering
}
