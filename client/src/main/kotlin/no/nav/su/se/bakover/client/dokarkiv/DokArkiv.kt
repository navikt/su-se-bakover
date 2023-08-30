package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.JournalpostCommand

interface DokArkiv {
    fun opprettJournalpost(dokumentInnhold: JournalpostCommand): Either<ClientError, JournalpostId>
}
