package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher

object UtbetalingStub : UtbetalingPublisher {
    override fun publish(
        tilUtbetaling: OversendelseTilOppdrag.TilUtbetaling
    ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Oppdragsmelding> = Oppdragsmelding(
        originalMelding = XmlMapper.writeValueAsString(toUtbetalingRequest(tilUtbetaling)),
        avstemmingsnøkkel = Avstemmingsnøkkel()
    ).right()
}
