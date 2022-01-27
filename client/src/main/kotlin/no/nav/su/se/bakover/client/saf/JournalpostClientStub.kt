package no.nav.su.se.bakover.client.saf

import arrow.core.Either
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost

object JournalpostClientStub : JournalpostClient {
    override fun hentJournalpost(journalpostId: JournalpostId): Either<KunneIkkeHenteJournalpost, HentetJournalpost> {
        TODO()
    }
}
