package no.nav.su.se.bakover.dokument.infrastructure.journalføring.søknad

import arrow.core.Either
import arrow.core.right
import dokument.domain.journalføring.søknad.JournalførSøknadClient
import dokument.domain.journalføring.søknad.JournalførSøknadCommand
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalpostIdGeneratorForFakes

/**
 * Åpen for å kunne aksesseres av tester.
 */
class JournalførSøknadFakeClient(
    private val journalpostIdGenerator: JournalpostIdGeneratorForFakes,
) : JournalførSøknadClient {
    override fun journalførSøknad(command: JournalførSøknadCommand): Either<ClientError, JournalpostId> {
        return journalpostIdGenerator.next().right()
    }
}
