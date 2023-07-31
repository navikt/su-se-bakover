package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent

sealed interface KunneIkkeLageBrevRequest {
    data class KunneIkkeHentePerson(
        val underliggende: no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson,
    ) : KunneIkkeLageBrevRequest

    data class KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(
        val underliggende: KunneIkkeHenteNavnForNavIdent,
    ) : KunneIkkeLageBrevRequest

    data object SkalIkkeSendeBrev : KunneIkkeLageBrevRequest
}
