package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher.KunneIkkeSendeUtbetaling
import java.time.Clock

class UtbetalingMqPublisher(
    private val mqPublisher: MqPublisher,
    private val clock: Clock = Clock.systemUTC()
) : UtbetalingPublisher {

    override fun publish(
        nyUtbetaling: NyUtbetaling
    ): Either<KunneIkkeSendeUtbetaling, Oppdragsmelding> {
        val tidspunkt = Tidspunkt.now(clock)
        val xml = XmlMapper.writeValueAsString(toUtbetalingRequest(nyUtbetaling))
        return mqPublisher.publish(xml)
            .mapLeft {
                KunneIkkeSendeUtbetaling(
                    Oppdragsmelding(
                        Oppdragsmelding.Oppdragsmeldingstatus.FEIL,
                        xml,
                        tidspunkt
                    )
                )
            }
            .map {
                Oppdragsmelding(
                    status = Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                    originalMelding = xml,
                    tidspunkt = tidspunkt
                )
            }
    }
}
