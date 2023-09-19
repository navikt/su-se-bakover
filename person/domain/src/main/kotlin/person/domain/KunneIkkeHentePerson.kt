package person.domain

sealed class KunneIkkeHentePerson {
    data object FantIkkePerson : KunneIkkeHentePerson()
    data object IkkeTilgangTilPerson : KunneIkkeHentePerson()
    data object Ukjent : KunneIkkeHentePerson()
}
