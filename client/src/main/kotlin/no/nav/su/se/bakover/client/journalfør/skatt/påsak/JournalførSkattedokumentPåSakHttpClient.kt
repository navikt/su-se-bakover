package no.nav.su.se.bakover.client.journalfør.skatt.påsak

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.Fagsak
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalPostType
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalførHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalførJsonRequest
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalførendeEnhet
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalpostDokument
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.tilBehandlingstema
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.tilBruker
import no.nav.su.se.bakover.domain.journalpost.JournalførSkattedokumentPåSakCommand
import no.nav.su.se.bakover.domain.skatt.JournalførSkattedokumentPåSakClient

/**
 * TODO jah: Flytt til skatt modul når vi har en skatt modul.
 */
internal class JournalførSkattedokumentPåSakHttpClient(
    private val client: JournalførHttpClient,
) : JournalførSkattedokumentPåSakClient {
    override fun journalførSkattedokument(command: JournalførSkattedokumentPåSakCommand): Either<ClientError, JournalpostId> {
        val dokument = command.dokument
        return client.opprettJournalpost(
            JournalførJsonRequest(
                tittel = dokument.dokumentTittel,
                journalpostType = JournalPostType.NOTAT,
                kanal = null,
                behandlingstema = command.sakstype.tilBehandlingstema(),
                journalfoerendeEnhet = JournalførendeEnhet.AUTOMATISK.enhet,
                avsenderMottaker = null,
                bruker = command.fnr.tilBruker(),
                sak = Fagsak(command.saksnummer.nummer.toString()),
                dokumenter = JournalpostDokument.lagDokumenterForJournalpostForSak(
                    tittel = dokument.dokumentTittel,
                    pdf = dokument.generertDokument,
                    originalJson = dokument.dokumentJson,
                ),
                datoDokument = dokument.skattedataHentet,
                eksternReferanseId = command.internDokumentId.toString(),
            ),
        )
    }
}
