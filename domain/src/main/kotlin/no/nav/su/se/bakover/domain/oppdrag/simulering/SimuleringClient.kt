package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag

interface SimuleringClient {
    fun simulerOppdrag(oppdrag: Oppdrag, oppdragGjelder: String): Either<SimuleringFeilet, Simulering>
}
