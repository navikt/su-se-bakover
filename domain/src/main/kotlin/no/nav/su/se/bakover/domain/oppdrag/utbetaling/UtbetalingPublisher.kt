package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.Either
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalingsrequest

interface UtbetalingPublisher {

    fun publishRequest(
        utbetalingsrequest: Utbetalingsrequest,
    ): Either<KunneIkkeSendeUtbetaling, Utbetalingsrequest>

    fun generateRequest(utbetaling: Utbetaling.SimulertUtbetaling): Utbetalingsrequest

    data class KunneIkkeSendeUtbetaling(
        val oppdragsmelding: Utbetalingsrequest,
    )
}
