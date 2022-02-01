package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import no.nav.su.se.bakover.domain.journal.JournalpostId

interface KlageClient {
    fun sendTilKlageinstans(
        klage: OversendtKlage,
        journalpostIdForVedtak: JournalpostId,
    ): Either<KunneIkkeOversendeTilKlageinstans, Unit>
}

object KunneIkkeOversendeTilKlageinstans
