package no.nav.su.se.bakover.service.vilkår

import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import java.util.UUID

data class LeggTilFastOppholdINorgeRequest(
    val behandlingId: UUID,
    val vilkår: FastOppholdINorgeVilkår.Vurdert,
)

sealed interface KunneIkkeLeggeFastOppholdINorgeVilkår {
    data class Søknadsbehandling(val feil: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår) :
        KunneIkkeLeggeFastOppholdINorgeVilkår

    data class Revurdering(val feil: no.nav.su.se.bakover.domain.revurdering.Revurdering.KunneIkkeLeggeTilFastOppholdINorgeVilkår) :
        KunneIkkeLeggeFastOppholdINorgeVilkår

    object FantIkkeBehandling : KunneIkkeLeggeFastOppholdINorgeVilkår

    data class UgyldigFastOppholdINorgeVikår(val feil: FastOppholdINorgeVilkår.Vurdert.UgyldigFastOppholdINorgeVikår) : KunneIkkeLeggeFastOppholdINorgeVilkår
}
