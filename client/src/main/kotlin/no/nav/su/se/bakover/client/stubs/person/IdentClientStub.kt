package no.nav.su.se.bakover.client.stubs.person

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent

object IdentClientStub : IdentClient {
    override fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeHenteNavnForNavIdent, String> =
        "Testbruker, Lokal".right()
}
