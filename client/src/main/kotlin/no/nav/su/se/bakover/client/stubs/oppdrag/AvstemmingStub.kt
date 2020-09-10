package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher

object AvstemmingStub : AvstemmingPublisher {
    override fun publish(
        utbetalinger: List<Utbetaling>
    ): Either<AvstemmingPublisher.KunneIkkeSendeAvstemming, Unit> = Unit.right()
}
