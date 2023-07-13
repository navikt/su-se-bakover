package no.nav.su.se.bakover.domain.vilkår.institusjonsopphold

import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import java.util.UUID

data class LeggTilInstitusjonsoppholdVilkårRequest(
    val behandlingId: UUID,
    val vilkår: InstitusjonsoppholdVilkår.Vurdert,
)

sealed interface KunneIkkeLeggeTilInstitusjonsoppholdVilkår {
    data class Søknadsbehandling(val feil: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår) :
        KunneIkkeLeggeTilInstitusjonsoppholdVilkår

    data class Revurdering(val feil: no.nav.su.se.bakover.domain.revurdering.Revurdering.KunneIkkeLeggeTilInstitusjonsoppholdVilkår) :
        KunneIkkeLeggeTilInstitusjonsoppholdVilkår

    data object FantIkkeBehandling : KunneIkkeLeggeTilInstitusjonsoppholdVilkår
}
