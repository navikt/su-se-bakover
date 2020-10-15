package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher.KunneIkkeSendeUtbetaling

class UtbetalingMqPublisher(
    private val mqPublisher: MqPublisher
) : UtbetalingPublisher {

    override fun publish(
        tilUtbetaling: OversendelseTilOppdrag.TilUtbetaling
    ): Either<KunneIkkeSendeUtbetaling, Oppdragsmelding> {
        val xml = XmlMapper.writeValueAsString(toUtbetalingRequest(tilUtbetaling))
        return mqPublisher.publish(xml)
            .mapLeft {
                KunneIkkeSendeUtbetaling(
                    Oppdragsmelding(
                        Oppdragsmelding.Oppdragsmeldingstatus.FEIL,
                        xml,
                        tilUtbetaling.avstemmingsnøkkel
                    )
                )
            }
            .map {
                Oppdragsmelding(
                    status = Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                    originalMelding = xml,
                    avstemmingsnøkkel = tilUtbetaling.avstemmingsnøkkel
                )
            }
    }
}
