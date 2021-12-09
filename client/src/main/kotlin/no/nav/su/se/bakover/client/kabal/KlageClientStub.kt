package no.nav.su.se.bakover.client.kabal

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.IverksattKlage
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeTilKlageinstans

object KlageClientStub : KlageClient {
    override fun sendTilKlageinstans(
        klage: IverksattKlage,
        journalpostIdForVedtak: JournalpostId,
    ): Either<KunneIkkeOversendeTilKlageinstans, Unit> = Unit.right()
}
