package no.nav.su.se.bakover.domain.person

sealed class KunneIkkeHenteNavnForNavIdent {
    override fun toString() = this::class.simpleName!!

    data object FeilVedHentingAvOnBehalfOfToken : KunneIkkeHenteNavnForNavIdent()

    data object KallTilMicrosoftGraphApiFeilet : KunneIkkeHenteNavnForNavIdent()

    data object DeserialiseringAvResponsFeilet : KunneIkkeHenteNavnForNavIdent()

    data object FantIkkeBrukerForNavIdent : KunneIkkeHenteNavnForNavIdent()
}
