package dokument.domain.journalføring.brev

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId

/**
 * Har ansvaret for å journalføre et utgående brev.
 */
interface JournalførBrevClient {
    fun journalførBrev(command: JournalførBrevCommand): Either<ClientError, JournalpostId>
}
