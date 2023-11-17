package no.nav.su.se.bakover.domain.skatt

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.JournalførSkattedokumentUtenforSakCommand

interface JournalførSkattedokumentUtenforSakClient {
    fun journalførSkattedokument(command: JournalførSkattedokumentUtenforSakCommand): Either<ClientError, JournalpostId>
}
