package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import java.time.Instant

interface UtbetalingPublisher {
    fun publish(
        nyUtbetaling: NyUtbetaling
    ): Either<KunneIkkeSendeUtbetaling, Oppdragsmelding>

    data class KunneIkkeSendeUtbetaling(
        val originalMelding: String,
        val tidspunkt: Instant = now()
    )
}
