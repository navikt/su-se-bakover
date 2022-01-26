package no.nav.su.se.bakover.client.saf

import arrow.core.Either
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.domain.journal.JournalpostId

object SafClientStub : SafClient {
    override fun hentJournalpost(journalpostId: JournalpostId): Either<KunneIkkeHenteJournalpost, Journalpost> {
        TODO()
    }
}
