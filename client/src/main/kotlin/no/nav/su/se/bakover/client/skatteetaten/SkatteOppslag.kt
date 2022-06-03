package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Skattemelding

interface SkatteOppslag {
    fun hentSkattemelding(accessToken: AccessToken, fnr: Fnr): Either<Feil, Skattemelding>
}

object Feil
