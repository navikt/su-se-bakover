package no.nav.su.se.bakover.client.kabal

import arrow.core.Either

interface KabalClient {
    fun sendTilKlageinstans(): Either<OversendelseFeilet, Unit>
}

object OversendelseFeilet
