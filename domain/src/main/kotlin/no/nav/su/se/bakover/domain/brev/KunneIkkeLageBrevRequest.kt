package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent

sealed interface KunneIkkeLageBrevRequest {
    object KunneIkkeHentePerson : KunneIkkeLageBrevRequest
    data class KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(
        val underliggende: KunneIkkeHenteNavnForNavIdent,
    ) : KunneIkkeLageBrevRequest
    object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrevRequest

    object SkalIkkeSendeBrev : KunneIkkeLageBrevRequest
}
