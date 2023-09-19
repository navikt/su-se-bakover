package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent

sealed interface KunneIkkeLageBrevRequest {
    data class KunneIkkeHentePerson(
        val underliggende: person.domain.KunneIkkeHentePerson,
    ) : KunneIkkeLageBrevRequest

    data class KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(
        val underliggende: KunneIkkeHenteNavnForNavIdent,
    ) : KunneIkkeLageBrevRequest

    data object SkalIkkeSendeBrev : KunneIkkeLageBrevRequest
}
