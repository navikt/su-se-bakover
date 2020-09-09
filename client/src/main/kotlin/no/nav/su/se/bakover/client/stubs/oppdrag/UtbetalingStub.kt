package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher

object UtbetalingStub : UtbetalingPublisher {
    override fun publish(
        oppdrag: Oppdrag,
        utbetaling: Utbetaling,
        oppdragGjelder: Fnr
    ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, String> = "".right()
}
