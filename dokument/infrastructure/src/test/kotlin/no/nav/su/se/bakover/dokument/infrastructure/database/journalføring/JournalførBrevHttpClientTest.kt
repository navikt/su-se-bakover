package no.nav.su.se.bakover.dokument.infrastructure.database.journalføring

import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.journalføring.brev.JournalførBrevCommand
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.brev.JournalførBrevHttpClient
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class JournalførBrevHttpClientTest {
    @Test
    fun `kan journalføre vedtaksbrev`() {
        val mock = mock<JournalførHttpClient> {
            on { opprettJournalpost(any()) } doReturn JournalpostId("1").right()
        }
        val client = JournalførBrevHttpClient(mock)
        val fnr = Fnr.generer()
        val metadata = Dokument.Metadata(
            sakId = UUID.randomUUID(),
            søknadId = UUID.randomUUID(),
            vedtakId = UUID.randomUUID(),
            revurderingId = UUID.randomUUID(),
            klageId = UUID.randomUUID(),
            tilbakekrevingsbehandlingId = UUID.randomUUID(),
            journalpostId = UUID.randomUUID().toString(),
            brevbestillingId = UUID.randomUUID().toString(),
        )
        val dokumentId = UUID.randomUUID()
        client.journalførBrev(
            JournalførBrevCommand(
                saksnummer = saksnummer,
                sakstype = Sakstype.UFØRE,
                dokument = Dokument.MedMetadata.Vedtak(
                    utenMetadata = Dokument.UtenMetadata.Vedtak(
                        id = dokumentId,
                        opprettet = fixedTidspunkt,
                        tittel = "tittel",
                        generertDokument = pdfATom(),
                        generertDokumentJson = "{}",
                    ),
                    metadata = metadata,
                ),
                fnr = fnr,
            ),
        ) shouldBe JournalpostId("1").right()
        verify(mock).opprettJournalpost(
            argThat {
                it shouldBe JournalførJsonRequest(
                    tittel = "tittel",
                    journalpostType = JournalPostType.UTGAAENDE,
                    tema = "SUP",
                    kanal = null,
                    behandlingstema = "ab0431",
                    journalfoerendeEnhet = "4815",
                    avsenderMottaker = AvsenderMottaker(id = fnr.toString()),
                    bruker = Bruker(id = fnr.toString()),
                    sak = Fagsak(saksnummer.toString()),
                    dokumenter = listOf(
                        JournalpostDokument(
                            tittel = "tittel",
                            brevkode = "XX.YY-ZZ",
                            dokumentvarianter = listOf(
                                DokumentVariant.ArkivPDF(""),
                                DokumentVariant.OriginalJson("e30="),
                            ),
                        ),
                    ),
                    datoDokument = fixedTidspunkt,
                    eksternReferanseId = dokumentId.toString(),
                )
            },
        )
    }
}
