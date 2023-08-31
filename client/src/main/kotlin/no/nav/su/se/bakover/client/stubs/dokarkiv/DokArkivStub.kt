package no.nav.su.se.bakover.client.stubs.dokarkiv

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.JournalpostCommand

data object DokArkivStub : DokArkiv {
    override fun opprettJournalpost(
        dokumentInnhold: JournalpostCommand,
    ): Either<ClientError, JournalpostId> = JournalpostId("1").right()
}
