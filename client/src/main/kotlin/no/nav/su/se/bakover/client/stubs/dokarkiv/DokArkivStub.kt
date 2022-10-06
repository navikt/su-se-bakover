package no.nav.su.se.bakover.client.stubs.dokarkiv

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.common.application.journal.JournalpostId

object DokArkivStub : DokArkiv {
    override fun opprettJournalpost(
        dokumentInnhold: Journalpost,
    ): Either<ClientError, JournalpostId> = JournalpostId("1").right()
}
