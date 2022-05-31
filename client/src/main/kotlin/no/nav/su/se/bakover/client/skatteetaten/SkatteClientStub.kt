package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.Fnr

class SkatteClientStub : SkatteOppslag {
    override fun hentSkattemelding(fnr: Fnr): Either<Unit, Skattemelding> {
        return Skattemelding(100).right()
    }
}
