package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher.KunneIkkeSendeUtbetaling

class UtbetalingMqPublisher(
    private val mqPublisher: MqPublisher
) : UtbetalingPublisher {

    override fun publish(
        utbetaling: Utbetaling
    ): Either<KunneIkkeSendeUtbetaling, Oppdragsmelding> {
        val xml = XmlMapper.writeValueAsString(toUtbetalingRequest(utbetaling))
        return mqPublisher.publish(xml)
            .mapLeft {
                KunneIkkeSendeUtbetaling(
                    Oppdragsmelding(
                        xml,
                        utbetaling.avstemmingsnøkkel
                    )
                )
            }
            .map {
                Oppdragsmelding(
                    originalMelding = xml,
                    avstemmingsnøkkel = utbetaling.avstemmingsnøkkel
                )
            }
    }
}
