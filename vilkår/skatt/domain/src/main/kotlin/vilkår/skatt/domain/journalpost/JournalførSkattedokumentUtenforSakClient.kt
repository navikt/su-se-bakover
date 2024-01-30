package vilkår.skatt.domain.journalpost

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId

interface JournalførSkattedokumentUtenforSakClient {
    fun journalførSkattedokument(command: JournalførSkattedokumentUtenforSakCommand): Either<ClientError, JournalpostId>
}
