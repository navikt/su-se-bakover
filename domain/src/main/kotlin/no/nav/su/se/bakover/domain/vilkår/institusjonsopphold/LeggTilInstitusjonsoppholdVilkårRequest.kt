package no.nav.su.se.bakover.domain.vilkår.institusjonsopphold

import no.nav.su.se.bakover.behandling.BehandlingsId
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår

data class LeggTilInstitusjonsoppholdVilkårRequest(
    val behandlingId: BehandlingsId,
    val vilkår: InstitusjonsoppholdVilkår.Vurdert,
)

sealed interface KunneIkkeLeggeTilInstitusjonsoppholdVilkår {
    data class Søknadsbehandling(val feil: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår) :
        KunneIkkeLeggeTilInstitusjonsoppholdVilkår

    data class Revurdering(val feil: no.nav.su.se.bakover.domain.revurdering.Revurdering.KunneIkkeLeggeTilInstitusjonsoppholdVilkår) :
        KunneIkkeLeggeTilInstitusjonsoppholdVilkår

    data object FantIkkeBehandling : KunneIkkeLeggeTilInstitusjonsoppholdVilkår
}
