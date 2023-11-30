package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher.KunneIkkeSendeUtbetaling
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalingsrequest

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
        val xml = xmlMapper.writeValueAsString(toUtbetalingRequest(utbetaling))
        return Utbetalingsrequest(xml)
    }
}
