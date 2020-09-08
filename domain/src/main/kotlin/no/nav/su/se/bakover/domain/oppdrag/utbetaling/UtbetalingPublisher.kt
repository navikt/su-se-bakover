package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

interface UtbetalingPublisher {
    fun publish(
        oppdrag: Oppdrag,
        utbetaling: Utbetaling,
        oppdragGjelder: Fnr
    ): Either<KunneIkkeSendeUtbetaling, Unit>

    object KunneIkkeSendeUtbetaling
}
