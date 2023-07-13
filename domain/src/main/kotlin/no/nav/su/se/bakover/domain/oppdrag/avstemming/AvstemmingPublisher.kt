package no.nav.su.se.bakover.domain.oppdrag.avstemming

import arrow.core.Either

interface AvstemmingPublisher {
    fun publish(grensesnittavstemming: Avstemming.Grensesnittavstemming): Either<KunneIkkeSendeAvstemming, Avstemming.Grensesnittavstemming>
    fun publish(konsistensavstemming: Avstemming.Konsistensavstemming.Ny): Either<KunneIkkeSendeAvstemming, Avstemming.Konsistensavstemming.Ny>

    data object KunneIkkeSendeAvstemming
}
