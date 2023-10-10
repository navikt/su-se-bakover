package no.nav.su.se.bakover.client.stubs.person

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import person.domain.IdentClient
import person.domain.KunneIkkeHenteNavnForNavIdent

data object IdentClientStub : IdentClient {
    override fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeHenteNavnForNavIdent, String> =
        "Testbruker, Lokal".right()
}
