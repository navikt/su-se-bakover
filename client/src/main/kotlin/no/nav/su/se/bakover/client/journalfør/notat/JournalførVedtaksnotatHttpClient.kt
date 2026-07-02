package no.nav.su.se.bakover.client.journalfør.notat

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.DokumentVariant
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.Fagsak
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalPostType
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførJsonRequest
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførendeEnhet
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalpostDokument
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.tilBehandlingstema
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.tilBruker
import no.nav.su.se.bakover.domain.notat.JournalførVedtaksnotatClient
import no.nav.su.se.bakover.domain.notat.JournalførVedtaksnotatCommand
import no.nav.su.se.bakover.domain.notat.JournalførbartVedlegg
import java.util.Base64

internal class JournalførVedtaksnotatHttpClient(
    private val client: JournalførHttpClient,
) : JournalførVedtaksnotatClient {
    override fun journalførVedtaksnotat(command: JournalførVedtaksnotatCommand): Either<ClientError, JournalpostId> {
        return client.opprettJournalpost(
            JournalførJsonRequest(
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
                eksternReferanseId = command.notatId.toString(),
            ),
        )
    }
}

fun createJournalførVedtaksnotatHttpClient(client: JournalførHttpClient): JournalførVedtaksnotatClient {
    return JournalførVedtaksnotatHttpClient(client)
}

private fun JournalførVedtaksnotatCommand.lagDokumenter(): List<JournalpostDokument> {
    return buildList {
        notatPdf?.let { pdf ->
            add(
                JournalpostDokument(
                    tittel = tittel,
                    dokumentvarianter = listOf(
                        // Joark krever at hvert dokument har en variant av typen ARKIV (PDF).
                        DokumentVariant.ArkivPDF(
                            fysiskDokument = Base64.getEncoder().encodeToString(pdf.getContent()),
                        ),
                        DokumentVariant.OriginalJson(
                            fysiskDokument = Base64.getEncoder().encodeToString(
                                serialize(
                                    VedtaksnotatPayload(
                                        notat = notat,
                                        attestantNotat = attestantNotat,
                                    ),
                                ).toByteArray(),
                            ),
                        ),
                    ),
                ),
            )
        }
        addAll(vedlegg.map { it.tilJournalpostDokument() })
    }
}

private fun JournalførbartVedlegg.tilJournalpostDokument(): JournalpostDokument {
    return JournalpostDokument(
        tittel = filnavn,
        dokumentvarianter = listOf(
            DokumentVariant.ArkivPDF(
                fysiskDokument = Base64.getEncoder().encodeToString(pdf.getContent()),
            ),
        ),
    )
}

private data class VedtaksnotatPayload(
    val notat: String,
    val attestantNotat: String,
)
