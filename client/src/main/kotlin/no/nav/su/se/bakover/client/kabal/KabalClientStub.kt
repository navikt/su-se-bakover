package no.nav.su.se.bakover.client.kabal

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.IverksattKlage

object KabalClientStub : KabalClient {
    override fun sendTilKlageinstans(klage: IverksattKlage, sak: Sak, journalpostIdForVedtak: JournalpostId): Either<KabalFeil, Unit> = Unit.right()
}
