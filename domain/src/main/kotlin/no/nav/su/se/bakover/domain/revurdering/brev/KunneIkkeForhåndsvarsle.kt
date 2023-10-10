package no.nav.su.se.bakover.domain.revurdering.brev

import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.revurdering.attestering.KunneIkkeSendeRevurderingTilAttestering

sealed class KunneIkkeForhåndsvarsle {
    data object FantIkkeRevurdering : KunneIkkeForhåndsvarsle()
    data object FantIkkePerson : KunneIkkeForhåndsvarsle()
    data object KunneIkkeOppdatereOppgave : KunneIkkeForhåndsvarsle()
    data object KunneIkkeHenteNavnForSaksbehandler : KunneIkkeForhåndsvarsle()
    data object UgyldigTilstand : KunneIkkeForhåndsvarsle()
    data class Attestering(val underliggende: KunneIkkeSendeRevurderingTilAttestering) : KunneIkkeForhåndsvarsle()
    data class KunneIkkeGenerereDokument(val underliggende: KunneIkkeLageDokument) : KunneIkkeForhåndsvarsle()
}
