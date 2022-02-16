package no.nav.su.se.bakover.client.stubs.person

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.bruker.IdentClient
import no.nav.su.se.bakover.domain.bruker.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.bruker.NavIdentBruker

object IdentClientStub : IdentClient {
    override fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeHenteNavnForNavIdent, String> =
        "Testbruker, Lokal".right()
}
