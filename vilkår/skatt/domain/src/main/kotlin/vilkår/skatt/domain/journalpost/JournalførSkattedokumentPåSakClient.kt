package vilkår.skatt.domain.journalpost

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId

interface JournalførSkattedokumentPåSakClient {
    fun journalførSkattedokument(command: JournalførSkattedokumentPåSakCommand): Either<ClientError, JournalpostId>
}
