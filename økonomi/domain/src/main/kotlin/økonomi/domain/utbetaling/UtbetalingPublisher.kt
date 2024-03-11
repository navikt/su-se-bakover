package økonomi.domain.utbetaling

import arrow.core.Either

interface UtbetalingPublisher {

    fun publishRequest(
        utbetalingsrequest: Utbetalingsrequest,
    ): Either<KunneIkkeSendeUtbetaling, Utbetalingsrequest>

    fun generateRequest(utbetaling: Utbetaling.SimulertUtbetaling): Utbetalingsrequest

    data class KunneIkkeSendeUtbetaling(
        val oppdragsmelding: Utbetalingsrequest,
    )
}
