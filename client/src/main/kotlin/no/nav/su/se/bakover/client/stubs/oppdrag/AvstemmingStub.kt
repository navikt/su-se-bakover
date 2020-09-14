package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher

object AvstemmingStub : AvstemmingPublisher {
    override fun publish(
        avstemming: Avstemming
    ): Either<AvstemmingPublisher.KunneIkkeSendeAvstemming, Avstemming> =
        avstemming.copy(avstemmingXmlRequest = "some xml message").right()
}
