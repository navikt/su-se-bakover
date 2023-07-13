package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent

sealed interface KunneIkkeLageBrevRequest {
    data object KunneIkkeHentePerson : KunneIkkeLageBrevRequest
    data class KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(
        val underliggende: KunneIkkeHenteNavnForNavIdent,
    ) : KunneIkkeLageBrevRequest
    data object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrevRequest

    data object SkalIkkeSendeBrev : KunneIkkeLageBrevRequest
}
