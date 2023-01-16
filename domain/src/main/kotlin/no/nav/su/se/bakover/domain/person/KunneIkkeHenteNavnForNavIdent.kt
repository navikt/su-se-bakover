package no.nav.su.se.bakover.domain.person

sealed class KunneIkkeHenteNavnForNavIdent {
    override fun toString() = this::class.simpleName!!

    object FeilVedHentingAvOnBehalfOfToken : KunneIkkeHenteNavnForNavIdent()

    object KallTilMicrosoftGraphApiFeilet : KunneIkkeHenteNavnForNavIdent()

    object DeserialiseringAvResponsFeilet : KunneIkkeHenteNavnForNavIdent()

    object FantIkkeBrukerForNavIdent : KunneIkkeHenteNavnForNavIdent()
}
