package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher.KunneIkkeSendeUtbetaling
import java.time.Instant

class UtbetalingMqPublisher(
    private val mqPublisher: MqPublisher
) : UtbetalingPublisher {

    override fun publish(
        nyUtbetaling: NyUtbetaling
    ): Either<KunneIkkeSendeUtbetaling, Oppdragsmelding> {
        val tidspunkt = Instant.now()
        val xml = XmlMapper.writeValueAsString(toUtbetalingRequest(nyUtbetaling, tidspunkt))
        return mqPublisher.publish(xml)
            .mapLeft { KunneIkkeSendeUtbetaling(xml, tidspunkt) }
            .map {
                Oppdragsmelding(
                    status = Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                    originalMelding = xml,
                    tidspunkt = tidspunkt
                )
            }
    }
}
