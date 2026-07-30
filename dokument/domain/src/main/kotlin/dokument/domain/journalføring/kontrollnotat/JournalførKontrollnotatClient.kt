package dokument.domain.journalføring.kontrollnotat

import JournalførKontrollnotatCommand
import arrow.core.Either
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId

interface JournalførKontrollnotatClient {
    fun journalførKontrollnotat(
        command: JournalførKontrollnotatCommand,
    ): Either<ClientError, JournalpostId>
}
