package no.nav.su.se.bakover.domain.vilkår.pensjon

import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import vilkår.pensjon.domain.KunneIkkeLagePensjonsVilkår
import vilkår.pensjon.domain.PensjonsVilkår

data class LeggTilPensjonsVilkårRequest(
    val behandlingId: BehandlingsId,
    val vilkår: PensjonsVilkår.Vurdert,
)

sealed interface KunneIkkeLeggeTilPensjonsVilkår {
    data class Søknadsbehandling(val feil: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår) :
        KunneIkkeLeggeTilPensjonsVilkår

    data class Revurdering(val feil: no.nav.su.se.bakover.domain.revurdering.Revurdering.KunneIkkeLeggeTilPensjonsVilkår) :
        KunneIkkeLeggeTilPensjonsVilkår

    data object FantIkkeBehandling : KunneIkkeLeggeTilPensjonsVilkår

    data class UgyldigPensjonsVilkår(val feil: KunneIkkeLagePensjonsVilkår) : KunneIkkeLeggeTilPensjonsVilkår
}
