package no.nav.su.se.bakover.domain.brev

sealed interface KunneIkkeLageBrevRequest {
    data class KunneIkkeHentePerson(
        val underliggende: person.domain.KunneIkkeHentePerson,
    ) : KunneIkkeLageBrevRequest

    data object SkalIkkeSendeBrev : KunneIkkeLageBrevRequest
}
