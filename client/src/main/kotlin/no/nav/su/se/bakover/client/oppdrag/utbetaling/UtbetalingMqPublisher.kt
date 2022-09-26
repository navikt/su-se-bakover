package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher.KunneIkkeSendeUtbetaling

class UtbetalingMqPublisher(
    private val mqPublisher: MqPublisher,
) : UtbetalingPublisher {

    override fun publishRequest(
        utbetalingsrequest: Utbetalingsrequest,
    ): Either<KunneIkkeSendeUtbetaling, Utbetalingsrequest> {
        return mqPublisher.publish(utbetalingsrequest.value)
            .mapLeft { KunneIkkeSendeUtbetaling(utbetalingsrequest) }
            .map { utbetalingsrequest }
    }

    override fun generateRequest(utbetaling: Utbetaling.SimulertUtbetaling): Utbetalingsrequest {
        val xml = XmlMapper.writeValueAsString(toUtbetalingRequest(utbetaling))
        return Utbetalingsrequest(xml)
    }
}
