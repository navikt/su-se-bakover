package no.nav.su.se.bakover.domain.bruker

import arrow.core.Either

interface IdentClient {
    fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeHenteNavnForNavIdent, String>
}

sealed interface KunneIkkeHenteNavnForNavIdent {
    object FeilVedHentingAvOnBehalfOfToken : KunneIkkeHenteNavnForNavIdent
    object KallTilMicrosoftGraphApiFeilet : KunneIkkeHenteNavnForNavIdent
    object DeserialiseringAvResponsFeilet : KunneIkkeHenteNavnForNavIdent
    object FantIkkeBrukerForNavIdent : KunneIkkeHenteNavnForNavIdent
}
