package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import no.nav.su.se.bakover.domain.Fnr

interface SkatteOppslag {
    fun hentSkattemelding(fnr: Fnr): Either<Unit, Skattemelding>
}

data class Skattemelding(val penger: Int)
