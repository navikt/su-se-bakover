package no.nav.su.se.bakover.service.journalføring

import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.Dokumentdistribusjon
import dokument.domain.JournalføringOgBrevdistribusjon
import dokument.domain.distribuering.DokDistFordeling
import dokument.domain.journalføring.brev.JournalførBrevClient
import dokument.domain.journalføring.brev.JournalførBrevCommand
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.backoff.Failures
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.service.dokument.JournalførDokumentService
import no.nav.su.se.bakover.service.skatt.JournalførSkattDokumentService
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.sakinfo
import no.nav.su.se.bakover.test.skatt.nySkattedokumentGenerert
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import vilkår.skatt.domain.DokumentSkattRepo
import vilkår.skatt.domain.Skattedokument
import vilkår.skatt.domain.journalpost.JournalførSkattedokumentPåSakClient
import vilkår.skatt.domain.journalpost.JournalførSkattedokumentPåSakCommand
import vilkår.skatt.domain.journalpost.JournalførSkattedokumentUtenforSakClient
import java.time.Clock
import java.util.UUID

class JournalføringServiceTest {

    @Test
    fun `journalfører dokumenter`() {
        val dokumentDis = dokumentdistribusjon()
        val skattDokument = nySkattedokumentGenerert(sakId = sakinfo.sakId)

        val sakService = mock<SakService> {
            on { hentSakInfo(any()) } doReturn sakinfo.right()
        }
        val dokumentRepo = mock<DokumentRepo> {
            on { this.hentDokumenterForJournalføring() } doReturn listOf(dokumentDis)
        }
        val dokumentSkattRepo = mock<DokumentSkattRepo> {
            on { this.hentDokumenterForJournalføring() } doReturn listOf(skattDokument)
        }
        val journalførBrevClient = mock<JournalførBrevClient> {
            on { journalførBrev(any()) } doReturn JournalpostId("1").right()
        }

        val journalførSkattedokumentPåSakClient = mock<JournalførSkattedokumentPåSakClient> {
            on { journalførSkattedokument(any()) } doReturn JournalpostId("1").right()
        }

        val serviceAndMocks = ServiceOgMocks(
            sakService = sakService,
            dokumentRepo = dokumentRepo,
            dokumentSkattRepo = dokumentSkattRepo,
            journalførBrevClient = journalførBrevClient,
            journalførSkattedokumentPåSakClient = journalførSkattedokumentPåSakClient,
        )
        serviceAndMocks.journalføringService.journalfør()
        verify(dokumentRepo).hentDokumenterForJournalføring()
        verify(dokumentSkattRepo).hentDokumenterForJournalføring()
        verify(sakService, times(2)).hentSakInfo(argThat { it shouldBe sakinfo.sakId })
        verify(dokumentRepo).oppdaterDokumentdistribusjon(
            argThat {
                it shouldBe dokumentDis.copy(
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                        JournalpostId("1"),
                        Failures.EMPTY,
                    ),
                )
            },
        )
        verify(dokumentSkattRepo).lagre(
            argThat { it shouldBe Skattedokument.Journalført(skattDokument, JournalpostId("1")) },
        )
        verify(journalførBrevClient).journalførBrev(
            argThat {
                it shouldBe JournalførBrevCommand(
                    fnr = sakinfo.fnr,
                    saksnummer = sakinfo.saksnummer,
                    dokument = dokumentDis.dokument,
                    sakstype = sakinfo.type,
                )
            },
        )
        verify(journalførSkattedokumentPåSakClient).journalførSkattedokument(
            argThat {
                it shouldBe JournalførSkattedokumentPåSakCommand(
                    saksnummer = sakinfo.saksnummer,
                    sakstype = sakinfo.type,
                    fnr = sakinfo.fnr,
                    dokument = skattDokument,
                )
            },
        )
        serviceAndMocks.verifyNoMoreInteractions()
    }

    private data class ServiceOgMocks(
        val journalførBrevClient: JournalførBrevClient = mock(),
        val dokDistFordeling: DokDistFordeling = mock(),
        val dokumentRepo: DokumentRepo = mock(),
        val dokumentSkattRepo: DokumentSkattRepo = mock(),
        val sakService: SakService = mock(),
        val clock: Clock = mock(),
        val sakInfo: SakInfo = sakinfo,
        val journalførSkattedokumentPåSakClient: JournalførSkattedokumentPåSakClient = mock(),
        val journalførSkattedokumentUtenforSakClient: JournalførSkattedokumentUtenforSakClient = mock(),
    ) {
        val journalføringService = JournalføringService(
            journalførDokumentService = JournalførDokumentService(
                journalførBrevClient = journalførBrevClient,
                dokumentRepo = dokumentRepo,
                sakService = sakService,
            ),
            journalførSkattDokumentService = JournalførSkattDokumentService(
                journalførSkattedokumentPåSakClient = journalførSkattedokumentPåSakClient,
                journalførSkattedokumentUtenforSakClient = journalførSkattedokumentUtenforSakClient,
                sakService = sakService,
                dokumentSkattRepo = dokumentSkattRepo,
            ),
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(
                journalførBrevClient,
                dokDistFordeling,
                dokumentRepo,
                dokumentSkattRepo,
                sakService,
                journalførSkattedokumentPåSakClient,
                journalførSkattedokumentUtenforSakClient,
            )
        }
    }
}

private fun dokumentdistribusjon(): Dokumentdistribusjon = Dokumentdistribusjon(
    id = UUID.randomUUID(),
    opprettet = fixedTidspunkt,
    dokument = Dokument.MedMetadata.Vedtak(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        tittel = "tittel",
        generertDokument = pdfATom(),
        generertDokumentJson = "{}",
        metadata = Dokument.Metadata(sakId = sakinfo.sakId),
        distribueringsadresse = null,
    ),
    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
)
