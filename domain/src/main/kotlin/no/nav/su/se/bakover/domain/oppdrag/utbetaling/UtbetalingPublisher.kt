package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

interface UtbetalingPublisher {
    fun publish(
        utbetaling: Utbetaling,
        oppdragGjelder: String
    ): Either<KunneIkkeSendeUtbetaling, Unit>

    object KunneIkkeSendeUtbetaling
}
