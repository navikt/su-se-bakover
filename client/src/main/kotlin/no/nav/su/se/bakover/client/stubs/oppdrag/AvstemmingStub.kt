package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.oppdrag.avstemming.lagGrensesnittavstemmingXml
import no.nav.su.se.bakover.client.oppdrag.avstemming.lagKonsistensavstemmingXml
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher

data object AvstemmingStub : AvstemmingPublisher {
    override fun publish(grensesnittavstemming: Avstemming.Grensesnittavstemming): Either<AvstemmingPublisher.KunneIkkeSendeAvstemming, Avstemming.Grensesnittavstemming> {
        return grensesnittavstemming.copy(avstemmingXmlRequest = lagGrensesnittavstemmingXml(grensesnittavstemming).toString())
            .right()
    }

    override fun publish(konsistensavstemming: Avstemming.Konsistensavstemming.Ny): Either<AvstemmingPublisher.KunneIkkeSendeAvstemming, Avstemming.Konsistensavstemming.Ny> {
        return konsistensavstemming.copy(avstemmingXmlRequest = lagKonsistensavstemmingXml(konsistensavstemming).toString())
            .right()
    }
}
