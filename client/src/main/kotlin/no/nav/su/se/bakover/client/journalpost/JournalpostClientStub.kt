package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import no.nav.su.se.bakover.domain.journalpost.Tema
import no.nav.su.se.bakover.domain.sak.Saksnummer

object JournalpostClientStub : JournalpostClient {
    override fun hentFerdigstiltJournalpost(
        saksnummer: Saksnummer,
        journalpostId: JournalpostId,
    ): Either<KunneIkkeHenteJournalpost, FerdigstiltJournalpost> {
        return FerdigstiltJournalpost.create(
            tema = Tema.SUP,
            journalstatus = JournalpostStatus.FERDIGSTILT,
            saksnummer = saksnummer,
        ).right()
    }
}
