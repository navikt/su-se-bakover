package no.nav.su.se.bakover.domain.person

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker

interface IdentClient {
    fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeHenteNavnForNavIdent, String>
}

sealed interface KunneIkkeHenteNavnForNavIdent {
    object FeilVedHentingAvOnBehalfOfToken : KunneIkkeHenteNavnForNavIdent
    object KallTilMicrosoftGraphApiFeilet : KunneIkkeHenteNavnForNavIdent
    object DeserialiseringAvResponsFeilet : KunneIkkeHenteNavnForNavIdent
    object FantIkkeBrukerForNavIdent : KunneIkkeHenteNavnForNavIdent
}
