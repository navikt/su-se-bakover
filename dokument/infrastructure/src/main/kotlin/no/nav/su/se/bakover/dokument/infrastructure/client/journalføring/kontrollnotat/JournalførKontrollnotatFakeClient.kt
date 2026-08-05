package no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.kontrollnotat

import arrow.core.Either
import arrow.core.right
import dokument.domain.journalføring.kontrollnotat.JournalførKontrollnotatClient
import dokument.domain.journalføring.kontrollnotat.JournalførKontrollnotatCommand
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalpostIdGeneratorForFakes

class JournalførKontrollnotatFakeClient(
    private val journalpostIdGenerator: JournalpostIdGeneratorForFakes,
) : JournalførKontrollnotatClient {
    override fun journalførKontrollnotat(command: JournalførKontrollnotatCommand): Either<ClientError, JournalpostId> {
        return journalpostIdGenerator.next().right()
    }
}
