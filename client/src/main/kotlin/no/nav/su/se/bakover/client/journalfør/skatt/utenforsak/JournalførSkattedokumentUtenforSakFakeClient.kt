package no.nav.su.se.bakover.client.journalfør.skatt.utenforsak

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalpostIdGeneratorForFakes
import vilkår.skatt.domain.journalpost.JournalførSkattedokumentUtenforSakClient
import vilkår.skatt.domain.journalpost.JournalførSkattedokumentUtenforSakCommand

/**
 * Åpen for å kunne aksesseres av tester.
 * TODO jah: Flytt til skatt modul når vi har en skatt modul.
 */
class JournalførSkattedokumentUtenforSakFakeClient(
    private val journalpostIdGenerator: JournalpostIdGeneratorForFakes,
) : JournalførSkattedokumentUtenforSakClient {
    override fun journalførSkattedokument(command: JournalførSkattedokumentUtenforSakCommand): Either<ClientError, JournalpostId> {
        return journalpostIdGenerator.next().right()
    }
}
