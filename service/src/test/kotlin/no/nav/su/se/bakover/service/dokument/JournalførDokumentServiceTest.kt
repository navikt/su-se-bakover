package no.nav.su.se.bakover.service.dokument

import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.Dokumentdistribusjon
import dokument.domain.JournalføringOgBrevdistribusjon
import dokument.domain.brev.KunneIkkeJournalføreDokument
import dokument.domain.journalføring.brev.JournalførBrevClient
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.sakinfo
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

class JournalførDokumentServiceTest {

    @Test
    fun `journalfør dokument - feil ved journalføring`() {
        val dokumentdistribusjon = dokumentdistribusjon()
        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokumenterForJournalføring(any()) } doReturn listOf(dokumentdistribusjon)
        }
        val journalførBrevClientMock =
            mock<JournalførBrevClient> { on { journalførBrev(any()) } doReturn ClientError(500, "kek").left() }
        val sakService = mock<SakService> {
            on { hentSakInfo(any()) } doReturn SakInfo(sakId, saksnummer, sakinfo.fnr, Sakstype.UFØRE).right()
        }

        ServiceOgMocks(
            sakService = sakService,
            journalførBrevClientMock = journalførBrevClientMock,
            dokumentRepo = dokumentRepo,
        ).let {
            it.journalførDokumentService.journalfør().let {
                it.size shouldBe 1
                it.first().shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Feil>()
                it.first().id shouldBe dokumentdistribusjon.id
                it.first().journalpostId shouldBe null
                it.first().brevbestillingsId shouldBe null
                (it.first() as JournalføringOgDistribueringsResultat.Feil).originalFeil shouldBe JournalføringOgDistribueringsResultat.JournalføringOgDistribueringsFeil.Journalføring(
                    KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost,
                )
            }
            verify(dokumentRepo).hentDokumenterForJournalføring()
            verify(sakService).hentSakInfo(argThat { it shouldBe sakId })
            verify(journalførBrevClientMock).journalførBrev(any())
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `journalfør dokument - dokument allerede journalført`() {
        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("done")))
        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokumenterForJournalføring(any()) } doReturn listOf(dokumentdistribusjon)
        }
        val sakService = mock<SakService> {
            on { hentSakInfo(any()) } doReturn SakInfo(sakId, saksnummer, sakinfo.fnr, Sakstype.UFØRE).right()
        }

        ServiceOgMocks(sakService = sakService, dokumentRepo = dokumentRepo).let {
            it.journalførDokumentService.journalfør().let {
                it.size shouldBe 1
                it.first().shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Ok>()
                it.first().id shouldBe dokumentdistribusjon.id
                it.first().journalpostId shouldBe dokumentdistribusjon.journalføringOgBrevdistribusjon.journalpostId()
                it.first().brevbestillingsId shouldBe dokumentdistribusjon.journalføringOgBrevdistribusjon.brevbestillingsId()
            }
            verify(dokumentRepo).hentDokumenterForJournalføring()
            verify(sakService).hentSakInfo(argThat { it shouldBe sakId })
            it.verifyNoMoreInteraction()
        }
    }

    private data class ServiceOgMocks(
        val journalførBrevClientMock: JournalførBrevClient = mock(),
        val dokumentRepo: DokumentRepo = mock(),
        val sakService: SakService = mock(),
    ) {
        val journalførDokumentService = JournalførDokumentService(
            dokumentRepo = dokumentRepo,
            journalførBrevClient = journalførBrevClientMock,
            sakService = sakService,
        )

        fun verifyNoMoreInteraction() {
            verifyNoMoreInteractions(journalførBrevClientMock, dokumentRepo, sakService)
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
        ),
        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
    )
}
