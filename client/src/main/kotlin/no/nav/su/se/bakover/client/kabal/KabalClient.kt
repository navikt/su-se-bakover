package no.nav.su.se.bakover.client.kabal

import arrow.core.Either
import no.nav.su.se.bakover.domain.klage.IverksattKlage

interface KabalClient {
    fun sendTilKlageinstans(klage: IverksattKlage): Either<OversendelseFeilet, Unit>
}

object OversendelseFeilet
