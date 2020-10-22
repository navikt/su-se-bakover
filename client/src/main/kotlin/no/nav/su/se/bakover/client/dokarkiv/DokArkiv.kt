package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError

interface DokArkiv {
    fun opprettJournalpost(dokumentInnhold: Journalpost): Either<ClientError, String>
}
