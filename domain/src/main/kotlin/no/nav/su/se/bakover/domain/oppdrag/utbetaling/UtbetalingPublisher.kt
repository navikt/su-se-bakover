package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

interface UtbetalingPublisher {
    fun publish(
        utbetaling: Utbetaling
    ): Either<KunneIkkeSendeUtbetaling, Oppdragsmelding>

    data class KunneIkkeSendeUtbetaling(
        val oppdragsmelding: Oppdragsmelding
    )
}
