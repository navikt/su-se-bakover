package no.nav.su.se.bakover.domain.vilkår.flyktning

import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import java.util.UUID

data class LeggTilFlyktningVilkårRequest(
    val behandlingId: UUID,
    val vilkår: FlyktningVilkår.Vurdert,
)

sealed interface KunneIkkeLeggeTilFlyktningVilkår {
    data class Søknadsbehandling(val feil: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFlyktningVilkår) :
        KunneIkkeLeggeTilFlyktningVilkår

    data class Revurdering(val feil: no.nav.su.se.bakover.domain.revurdering.Revurdering.KunneIkkeLeggeTilFlyktningVilkår) :
        KunneIkkeLeggeTilFlyktningVilkår

    data object FantIkkeBehandling : KunneIkkeLeggeTilFlyktningVilkår

    data class UgyldigFlyktningVilkår(val feil: FlyktningVilkår.Vurdert.UgyldigFlyktningVilkår) :
        KunneIkkeLeggeTilFlyktningVilkår
}
