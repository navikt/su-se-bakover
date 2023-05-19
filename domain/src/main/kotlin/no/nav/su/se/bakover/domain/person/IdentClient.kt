package no.nav.su.se.bakover.domain.person

import arrow.core.Either
import no.nav.su.se.bakover.common.ident.NavIdentBruker

interface IdentClient {
    fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeHenteNavnForNavIdent, String>
}
