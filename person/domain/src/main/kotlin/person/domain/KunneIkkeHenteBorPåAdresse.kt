package person.domain

sealed interface KunneIkkeHenteBorPåAdresse {
    data object FantIkkePerson : KunneIkkeHenteBorPåAdresse
    data object IkkeTilgangTilPerson : KunneIkkeHenteBorPåAdresse
    data object FantIkkeAdresse : KunneIkkeHenteBorPåAdresse
    data object OppslagFeilet : KunneIkkeHenteBorPåAdresse
    data object Ukjent : KunneIkkeHenteBorPåAdresse
}
