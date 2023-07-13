package no.nav.su.se.bakover.domain.person

sealed class KunneIkkeHentePerson {
    data object FantIkkePerson : KunneIkkeHentePerson()
    data object IkkeTilgangTilPerson : KunneIkkeHentePerson()
    data object Ukjent : KunneIkkeHentePerson()
}
