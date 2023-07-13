package no.nav.su.se.bakover.domain.person

sealed class KunneIkkeHentePerson {
    override fun toString() = this::class.simpleName!!
    data object FantIkkePerson : KunneIkkeHentePerson()
    data object IkkeTilgangTilPerson : KunneIkkeHentePerson()
    data object Ukjent : KunneIkkeHentePerson()
}
