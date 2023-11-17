package dokument.domain.journalføring.søknad

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId

/**
 * Har ansvaret for å journalføre en søknad.
 */
interface JournalførSøknadClient {
    fun journalførSøknad(command: JournalførSøknadCommand): Either<ClientError, JournalpostId>
}
