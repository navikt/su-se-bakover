package no.nav.su.se.bakover.service.dokument

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.dokdistfordeling.KunneIkkeBestilleDistribusjon
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.Distribusjonstidspunkt
import no.nav.su.se.bakover.domain.brev.Distribusjonstype
import no.nav.su.se.bakover.domain.brev.KunneIkkeBestilleBrevForDokument
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.sakinfo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Year
import java.util.UUID

internal class DistribuerDokumentServiceTest {

    val fnr = sakinfo.fnr
    val person = Person(
        ident = Ident(
            fnr = sakinfo.fnr,
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = null, etternavn = "Strømøy"),
        fødsel = Person.Fødsel.MedFødselsår(
            år = Year.of(1956),
        ),
    )
    private val distribusjonstype = Distribusjonstype.VIKTIG
    private val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID

    @Test
    fun `distribuerer dokumenter`() {
        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("very")))

        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokumenterForDistribusjon() } doReturn listOf(dokumentdistribusjon)
        }

        val dokdistFordeling = mock<DokDistFordeling> {
            on { this.bestillDistribusjon(any(), any(), any()) } doReturn BrevbestillingId("id").right()
        }

        ServiceOgMocks(
            dokumentRepo = dokumentRepo,
            dokDistFordeling = dokdistFordeling,
        ).dokumentService.distribuer()

        verify(dokumentRepo, times(1)).hentDokumenterForDistribusjon()

        verify(dokdistFordeling).bestillDistribusjon(
            argThat { it shouldBe dokumentdistribusjon.journalføringOgBrevdistribusjon.journalpostId() },
            argThat { it shouldBe Distribusjonstype.VEDTAK },
            argThat { it shouldBe Distribusjonstidspunkt.KJERNETID },
        )
        verify(dokumentRepo, times(1)).oppdaterDokumentdistribusjon(
            dokumentdistribusjon.copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    JournalpostId("very"),
                    BrevbestillingId("id"),
                ),
            ),
        )
    }

    @Test
    fun `distribuerer brev`() {
        val dockdistMock = mock<DokDistFordeling> {
            on {
                bestillDistribusjon(
                    JournalpostId("journalpostId"),
                    distribusjonstype,
                    distribusjonstidspunkt,
                )
            } doReturn BrevbestillingId("en bestillings id").right()
        }

        ServiceOgMocks(dokDistFordeling = dockdistMock)
            .dokumentService.distribuerBrev(
                JournalpostId("journalpostId"),
                distribusjonstype,
                distribusjonstidspunkt,
            ) shouldBe BrevbestillingId("en bestillings id").right()

        verify(dockdistMock).bestillDistribusjon(
            JournalpostId("journalpostId"),
            distribusjonstype,
            distribusjonstidspunkt,
        )
    }

    @Test
    fun `distribuer dokument - ikke journalført`() {
        val dokumentdistribusjon = dokumentdistribusjon()

        ServiceOgMocks().let {
            it.dokumentService.distribuerDokument(dokumentdistribusjon) shouldBe KunneIkkeBestilleBrevForDokument.MåJournalføresFørst.left()
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `distribuer dokument - allerede distribuert`() {
        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    JournalpostId("very"),
                    BrevbestillingId("happy"),
                ),
            )

        ServiceOgMocks().let {
            it.dokumentService.distribuerDokument(dokumentdistribusjon) shouldBe dokumentdistribusjon.right()
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `distribuer dokument - feil ved bestilling av brev`() {
        val dokDistMock = mock<DokDistFordeling> {
            on { bestillDistribusjon(any(), any(), any()) } doReturn KunneIkkeBestilleDistribusjon.left()
        }

        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("sad")))

        ServiceOgMocks(
            dokDistFordeling = dokDistMock,
        ).let {
            it.dokumentService.distribuerDokument(dokumentdistribusjon) shouldBe KunneIkkeBestilleBrevForDokument.FeilVedBestillingAvBrev.left()
            verify(dokDistMock).bestillDistribusjon(
                JournalpostId("sad"),
                Distribusjonstype.VEDTAK,
                distribusjonstidspunkt,
            )
            it.verifyNoMoreInteraction()
        }
    }

    private data class ServiceOgMocks(
        val dokDistFordeling: DokDistFordeling = mock(),
        val dokumentRepo: DokumentRepo = mock(),
    ) {
        val dokumentService = DistribuerDokumentServiceImpl(
            dokDistFordeling = dokDistFordeling,
            dokumentRepo = dokumentRepo,
        )

        fun verifyNoMoreInteraction() {
            verifyNoMoreInteractions(dokDistFordeling, dokumentRepo)
        }
    }

    private fun dokumentdistribusjon(): Dokumentdistribusjon = Dokumentdistribusjon(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        dokument = Dokument.MedMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = "".toByteArray(),
            generertDokumentJson = "{}",
            metadata = Dokument.Metadata(sakId = sakinfo.sakId),
        ),
        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
    )
}
