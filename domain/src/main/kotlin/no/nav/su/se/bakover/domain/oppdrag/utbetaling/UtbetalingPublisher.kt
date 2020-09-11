package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling

interface UtbetalingPublisher {
    fun publish(
        nyUtbetaling: NyUtbetaling
    ): Either<KunneIkkeSendeUtbetaling, String>

    data class KunneIkkeSendeUtbetaling(val originalMelding: String)
}
