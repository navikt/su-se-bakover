package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId

interface DokArkiv {
    fun opprettJournalpost(dokumentInnhold: Journalpost): Either<ClientError, JournalpostId>
}
