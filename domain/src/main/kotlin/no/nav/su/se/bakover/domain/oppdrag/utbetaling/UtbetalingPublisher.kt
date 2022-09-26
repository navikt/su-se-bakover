package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest

interface UtbetalingPublisher {

    fun publishRequest(
        utbetalingsrequest: Utbetalingsrequest,
    ): Either<KunneIkkeSendeUtbetaling, Utbetalingsrequest>

    fun generateRequest(utbetaling: Utbetaling.SimulertUtbetaling): Utbetalingsrequest

    data class KunneIkkeSendeUtbetaling(
        val oppdragsmelding: Utbetalingsrequest,
    )
}
