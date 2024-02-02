package person.domain

sealed interface KunneIkkeHentePerson {
    data object FantIkkePerson : KunneIkkeHentePerson
    data object IkkeTilgangTilPerson : KunneIkkeHentePerson
    data object Ukjent : KunneIkkeHentePerson
}
