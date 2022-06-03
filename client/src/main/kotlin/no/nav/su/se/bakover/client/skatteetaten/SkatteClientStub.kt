package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Skattemelding

class SkatteClientStub : SkatteOppslag {
    override fun hentSkattemelding(accessToken: AccessToken, fnr: Fnr): Either<Feil, Skattemelding> {
        return Skattemelding(100).right()
    }
}
