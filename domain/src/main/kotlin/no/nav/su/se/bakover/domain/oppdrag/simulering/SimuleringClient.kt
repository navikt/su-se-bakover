package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

interface SimuleringClient {
    fun simulerOppdrag(utbetaling: Utbetaling, utbetalingGjelder: String): Either<SimuleringFeilet, Simulering>
}
