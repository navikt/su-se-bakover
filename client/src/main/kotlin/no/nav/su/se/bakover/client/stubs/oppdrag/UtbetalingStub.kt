package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher

object UtbetalingStub : UtbetalingPublisher {
    override fun publish(
        utbetaling: Utbetaling.SimulertUtbetaling
    ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Utbetalingsrequest> = Utbetalingsrequest(
        value = XmlMapper.writeValueAsString(toUtbetalingRequest(utbetaling))
    ).right()
}
