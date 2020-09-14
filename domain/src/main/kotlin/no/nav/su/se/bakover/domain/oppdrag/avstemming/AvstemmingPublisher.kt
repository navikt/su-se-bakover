package no.nav.su.se.bakover.domain.oppdrag.avstemming

import arrow.core.Either

interface AvstemmingPublisher {
    fun publish(
        avstemming: Avstemming
    ): Either<KunneIkkeSendeAvstemming, Avstemming>

    object KunneIkkeSendeAvstemming
}
