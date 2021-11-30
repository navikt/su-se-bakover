package no.nav.su.se.bakover.client.kabal

import arrow.core.Either
import arrow.core.right

object KabalClientStub: KabalClient {
    override fun sendTilKlageinstans(): Either<OversendelseFeilet, Unit> = Unit.right()
}
