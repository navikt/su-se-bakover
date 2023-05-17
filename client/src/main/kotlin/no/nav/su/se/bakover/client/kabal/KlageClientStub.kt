package no.nav.su.se.bakover.client.kabal

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeTilKlageinstans
import no.nav.su.se.bakover.domain.klage.OversendtKlage

object KlageClientStub : KlageClient {
    override fun sendTilKlageinstans(
        klage: OversendtKlage,
        journalpostIdForVedtak: JournalpostId,
    ): Either<KunneIkkeOversendeTilKlageinstans, Unit> = Unit.right()
}
