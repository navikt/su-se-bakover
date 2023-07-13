package no.nav.su.se.bakover.domain.person

sealed class KunneIkkeHenteNavnForNavIdent {
    data object FeilVedHentingAvOnBehalfOfToken : KunneIkkeHenteNavnForNavIdent()

    data object KallTilMicrosoftGraphApiFeilet : KunneIkkeHenteNavnForNavIdent()

    data object DeserialiseringAvResponsFeilet : KunneIkkeHenteNavnForNavIdent()

    data object FantIkkeBrukerForNavIdent : KunneIkkeHenteNavnForNavIdent()
}
