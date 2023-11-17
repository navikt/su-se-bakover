package no.nav.su.se.bakover.domain.skatt

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.JournalførSkattedokumentPåSakCommand

interface JournalførSkattedokumentPåSakClient {
    fun journalførSkattedokument(command: JournalførSkattedokumentPåSakCommand): Either<ClientError, JournalpostId>
}
