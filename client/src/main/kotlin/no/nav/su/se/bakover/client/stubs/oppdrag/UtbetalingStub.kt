package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher

object UtbetalingStub : UtbetalingPublisher {
    override fun publish(
        nyUtbetaling: NyUtbetaling
    ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, String> =
        XmlMapper.writeValueAsString(toUtbetalingRequest(nyUtbetaling)).right()
}
