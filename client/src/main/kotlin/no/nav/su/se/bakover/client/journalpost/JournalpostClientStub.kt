package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost

object JournalpostClientStub : JournalpostClient {
    override fun hentJournalpost(journalpostId: JournalpostId): Either<KunneIkkeHenteJournalpost, HentetJournalpost> {
        return HentetJournalpost.create(
            tema = "SUP",
        ).right()
    }
}
