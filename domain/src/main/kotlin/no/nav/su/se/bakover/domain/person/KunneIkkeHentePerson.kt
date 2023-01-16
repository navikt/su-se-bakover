package no.nav.su.se.bakover.domain.person

sealed class KunneIkkeHentePerson {
    override fun toString() = this::class.simpleName!!
    object FantIkkePerson : KunneIkkeHentePerson()
    object IkkeTilgangTilPerson : KunneIkkeHentePerson()
    object Ukjent : KunneIkkeHentePerson()
}
