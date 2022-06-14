package no.nav.su.se.bakover.service.vilkår

import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLagePensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import java.util.UUID

data class LeggTilPensjonsVilkårRequest(
    val behandlingId: UUID,
    val vilkår: PensjonsVilkår.Vurdert,
)

sealed interface KunneIkkeLeggeTilPensjonsVilkår {
    data class Søknadsbehandling(val feil: no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling.KunneIkkeLeggeTilPensjonsVilkår) :
        KunneIkkeLeggeTilPensjonsVilkår

    data class Revurdering(val feil: no.nav.su.se.bakover.domain.revurdering.Revurdering.KunneIkkeLeggeTilPensjonsVilkår) :
        KunneIkkeLeggeTilPensjonsVilkår

    object FantIkkeBehandling : KunneIkkeLeggeTilPensjonsVilkår

    data class UgyldigPensjonsVilkår(val feil: KunneIkkeLagePensjonsVilkår) : KunneIkkeLeggeTilPensjonsVilkår
}
