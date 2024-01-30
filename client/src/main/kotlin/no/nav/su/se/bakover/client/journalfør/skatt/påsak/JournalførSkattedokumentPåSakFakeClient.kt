package no.nav.su.se.bakover.client.journalfør.skatt.påsak

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalpostIdGeneratorForFakes
import vilkår.skatt.domain.journalpost.JournalførSkattedokumentPåSakClient
import vilkår.skatt.domain.journalpost.JournalførSkattedokumentPåSakCommand

/**
 * Åpen for å kunne aksesseres av tester.
 * TODO jah: Flytt til skatt modul når vi har en skatt modul.
 */
class JournalførSkattedokumentPåSakFakeClient(
    private val journalpostIdGenerator: JournalpostIdGeneratorForFakes,
) : JournalførSkattedokumentPåSakClient {
    override fun journalførSkattedokument(command: JournalførSkattedokumentPåSakCommand): Either<ClientError, JournalpostId> {
        return journalpostIdGenerator.next().right()
    }
}
