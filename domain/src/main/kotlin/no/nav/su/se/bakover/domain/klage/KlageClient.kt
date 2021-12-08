package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId

interface KlageClient {
    fun sendTilKlageinstans(
        klage: IverksattKlage,
        saksnummer: Saksnummer,
        fnr: Fnr,
        journalpostIdForVedtak: JournalpostId,
    ): Either<KunneIkkeOversendeTilKlageinstans, Unit>
}

object KunneIkkeOversendeTilKlageinstans
