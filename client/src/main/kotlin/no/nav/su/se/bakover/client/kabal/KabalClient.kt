package no.nav.su.se.bakover.client.kabal

import arrow.core.Either
import no.nav.su.se.bakover.domain.klage.IverksattKlage

interface KabalClient {
    fun sendTilKlageinstans(klage: IverksattKlage): Either<KabalFeil, Unit>
}

sealed class KabalFeil {
    object KunneIkkeLageToken : KabalFeil()
    object OversendelseFeilet : KabalFeil()
}
