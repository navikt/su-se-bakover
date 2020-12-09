package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest

interface UtbetalingPublisher {
    fun publish(
        utbetaling: Utbetaling.SimulertUtbetaling
    ): Either<KunneIkkeSendeUtbetaling, Utbetalingsrequest>

    data class KunneIkkeSendeUtbetaling(
        val oppdragsmelding: Utbetalingsrequest
    )
}
