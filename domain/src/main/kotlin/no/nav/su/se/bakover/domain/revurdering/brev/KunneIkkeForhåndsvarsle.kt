package no.nav.su.se.bakover.domain.revurdering.brev

import no.nav.su.se.bakover.domain.revurdering.attestering.KunneIkkeSendeRevurderingTilAttestering

sealed class KunneIkkeForhåndsvarsle {
    object FantIkkeRevurdering : KunneIkkeForhåndsvarsle()
    object FantIkkePerson : KunneIkkeForhåndsvarsle()
    object KunneIkkeOppdatereOppgave : KunneIkkeForhåndsvarsle()
    object KunneIkkeHenteNavnForSaksbehandler : KunneIkkeForhåndsvarsle()
    object UgyldigTilstand : KunneIkkeForhåndsvarsle()
    data class Attestering(val subError: KunneIkkeSendeRevurderingTilAttestering) : KunneIkkeForhåndsvarsle()
    object KunneIkkeGenerereDokument : KunneIkkeForhåndsvarsle()
}
