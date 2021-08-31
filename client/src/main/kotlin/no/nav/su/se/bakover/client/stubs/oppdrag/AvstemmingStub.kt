package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher

object AvstemmingStub : AvstemmingPublisher {
    override fun publish(grensesnittavstemming: Avstemming.Grensesnittavstemming): Either<AvstemmingPublisher.KunneIkkeSendeAvstemming, Avstemming.Grensesnittavstemming> {
        return grensesnittavstemming.copy(avstemmingXmlRequest = "some xml message").right()
    }

    override fun publish(konsistensavstemming: Avstemming.Konsistensavstemming): Either<AvstemmingPublisher.KunneIkkeSendeAvstemming, Avstemming.Konsistensavstemming> {
        return konsistensavstemming.copy(avstemmingXmlRequest = "some xml message").right()
    }
}
