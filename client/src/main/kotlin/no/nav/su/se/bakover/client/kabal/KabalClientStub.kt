package no.nav.su.se.bakover.client.kabal

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.klage.IverksattKlage

object KabalClientStub : KabalClient {
    override fun sendTilKlageinstans(klage: IverksattKlage): Either<OversendelseFeilet, Unit> = Unit.right()
}
