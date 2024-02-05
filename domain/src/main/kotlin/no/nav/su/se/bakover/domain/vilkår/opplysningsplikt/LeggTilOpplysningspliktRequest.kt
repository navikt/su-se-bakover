package no.nav.su.se.bakover.domain.vilkår.opplysningsplikt

import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import vilkår.opplysningsplikt.domain.KunneIkkeLageOpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår

sealed interface LeggTilOpplysningspliktRequest {
    val behandlingId: BehandlingsId
    val vilkår: OpplysningspliktVilkår.Vurdert

    data class Søknadsbehandling(
        override val behandlingId: SøknadsbehandlingId,
        override val vilkår: OpplysningspliktVilkår.Vurdert,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : LeggTilOpplysningspliktRequest

    data class Revurdering(
        override val behandlingId: RevurderingId,
        override val vilkår: OpplysningspliktVilkår.Vurdert,
    ) : LeggTilOpplysningspliktRequest
}

sealed interface KunneIkkeLeggeTilOpplysningsplikt {
    data class Søknadsbehandling(val feil: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt) :
        KunneIkkeLeggeTilOpplysningsplikt

    data class Revurdering(val feil: no.nav.su.se.bakover.domain.revurdering.Revurdering.KunneIkkeLeggeTilOpplysningsplikt) :
        KunneIkkeLeggeTilOpplysningsplikt

    data object FantIkkeBehandling : KunneIkkeLeggeTilOpplysningsplikt

    data class UgyldigOpplysningspliktVilkår(val feil: KunneIkkeLageOpplysningspliktVilkår) :
        KunneIkkeLeggeTilOpplysningsplikt
}
