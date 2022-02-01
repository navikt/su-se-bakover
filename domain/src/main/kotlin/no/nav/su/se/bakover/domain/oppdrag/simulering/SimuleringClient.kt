package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either

interface SimuleringClient {
    fun simulerUtbetaling(request: SimulerUtbetalingRequest): Either<SimuleringFeilet, Simulering>
}
