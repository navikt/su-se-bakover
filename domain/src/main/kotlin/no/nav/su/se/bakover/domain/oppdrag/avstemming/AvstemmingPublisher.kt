package no.nav.su.se.bakover.domain.oppdrag.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

interface AvstemmingPublisher {
    fun publish(
        utbetalinger: List<Utbetaling>
    ): Either<KunneIkkeSendeAvstemming, Unit>

    object KunneIkkeSendeAvstemming
}
