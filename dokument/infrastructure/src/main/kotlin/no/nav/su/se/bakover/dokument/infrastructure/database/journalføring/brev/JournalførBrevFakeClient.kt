package no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.brev

import arrow.core.Either
import arrow.core.right
import dokument.domain.journalføring.brev.JournalførBrevClient
import dokument.domain.journalføring.brev.JournalførBrevCommand
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalpostIdGeneratorForFakes

/**
 * Åpen for å kunne aksesseres av tester.
 */
class JournalførBrevFakeClient(
    private val journalpostIdGenerator: JournalpostIdGeneratorForFakes,
) : JournalførBrevClient {
    override fun journalførBrev(command: JournalførBrevCommand): Either<ClientError, JournalpostId> {
        return journalpostIdGenerator.next().right()
    }
}
