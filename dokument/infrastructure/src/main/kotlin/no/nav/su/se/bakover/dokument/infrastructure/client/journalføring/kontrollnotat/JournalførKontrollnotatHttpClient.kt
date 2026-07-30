import arrow.core.Either
import dokument.domain.journalføring.kontrollnotat.JournalførKontrollnotatClient
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.DokumentVariant
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.Fagsak
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalPostType
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførJsonRequest
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførendeEnhet
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalpostDokument
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.tilBehandlingstema
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.tilBruker
import java.util.Base64

internal class JournalførKontrollnotatHttpClient(
    private val client: JournalførHttpClient,
) : JournalførKontrollnotatClient {
    override fun journalførKontrollnotat(
        command: JournalførKontrollnotatCommand,
    ): Either<ClientError, JournalpostId> {
        return client.opprettJournalpost(
            jsonDto = JournalførJsonRequest(
                tittel = command.tittel,
                journalpostType = JournalPostType.NOTAT,
                kanal = null,
                behandlingstema = command.sakstype.tilBehandlingstema(),
                journalfoerendeEnhet = JournalførendeEnhet.ÅLESUND.enhet,
                avsenderMottaker = null,
                bruker = command.fnr.tilBruker(),
                sak = Fagsak(command.saksnummer.nummer.toString()),
                dokumenter = command.lagDokumenter(),
                datoDokument = command.datoDokument,
                eksternReferanseId = command.kontrollsamtaleNotatId.toString(),
            ),
        )
    }
}

private fun JournalførKontrollnotatCommand.lagDokumenter(): List<JournalpostDokument> {
    return listOf(
        JournalpostDokument(
            tittel = tittel,
            dokumentvarianter = listOf(
                DokumentVariant.ArkivPDF(
                    fysiskDokument = Base64.getEncoder().encodeToString(kontrollnotatPdf.getContent()),
                ),
                DokumentVariant.OriginalJson(
                    fysiskDokument = Base64.getEncoder().encodeToString(
                        kontrollnotatJson.toByteArray(),
                    ),
                ),
            ),
        ),
    )
}

fun createJournalførKontrollnotatHttpClient(client: JournalførHttpClient): JournalførKontrollnotatClient {
    return JournalførKontrollnotatHttpClient(client)
}
