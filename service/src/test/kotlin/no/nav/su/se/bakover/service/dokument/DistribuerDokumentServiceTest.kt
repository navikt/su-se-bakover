package no.nav.su.se.bakover.service.dokument

import arrow.core.left
import arrow.core.right
import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.Dokumentdistribusjon
import dokument.domain.JournalføringOgBrevdistribusjon
import dokument.domain.brev.BrevbestillingId
import dokument.domain.brev.KunneIkkeBestilleBrevForDokument
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.dokdistfordeling.KunneIkkeBestilleDistribusjon
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.sakinfo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.Person
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

        ServiceOgMocks(dokumentRepo = dokumentRepo, dokDistFordeling = dokdistFordeling).dokumentService.distribuer()
            .let {
                it.size shouldBe 1
                it.first().shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Ok>()
                it.first().brevbestillingsId shouldBe BrevbestillingId("id")
            }

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
    fun `distribuer dokument - ikke journalført`() {
        val dokumentdistribusjon = dokumentdistribusjon()
        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokumenterForDistribusjon(any()) } doReturn listOf(dokumentdistribusjon)
        }
        ServiceOgMocks(dokumentRepo = dokumentRepo).let {
            it.dokumentService.distribuer().let {
                it.size shouldBe 1
                it.first().shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Feil>()
                it.first().id shouldBe dokumentdistribusjon.id
                (it.first() as JournalføringOgDistribueringsResultat.Feil).originalFeil shouldBe
                    JournalføringOgDistribueringsResultat.JournalføringOgDistribueringsFeil.Distribuering(
                        KunneIkkeBestilleBrevForDokument.MåJournalføresFørst,
                    )
            }
            verify(dokumentRepo).hentDokumenterForDistribusjon()
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
        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokumenterForDistribusjon(any()) } doReturn listOf(dokumentdistribusjon)
        }

        ServiceOgMocks(dokumentRepo = dokumentRepo).let {
            it.dokumentService.distribuer().let {
                it.size shouldBe 1
                it.first().shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Ok>()
                it.first().id shouldBe dokumentdistribusjon.id
                it.first().journalpostId shouldBe dokumentdistribusjon.journalføringOgBrevdistribusjon.journalpostId()
                it.first().brevbestillingsId shouldBe dokumentdistribusjon.journalføringOgBrevdistribusjon.brevbestillingsId()
            }
            verify(dokumentRepo).hentDokumenterForDistribusjon()
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `distribuer dokument - feil ved bestilling av brev`() {
        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("sad")))
        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokumenterForDistribusjon(any()) } doReturn listOf(dokumentdistribusjon)
        }
        val dokDistMock = mock<DokDistFordeling> {
            on { bestillDistribusjon(any(), any(), any()) } doReturn KunneIkkeBestilleDistribusjon.left()
        }

        ServiceOgMocks(
            dokDistFordeling = dokDistMock,
            dokumentRepo = dokumentRepo,
        ).let {
            it.dokumentService.distribuer().let {
                it.size shouldBe 1
                it.first().shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Feil>()
                it.first().id shouldBe dokumentdistribusjon.id
                (it.first() as JournalføringOgDistribueringsResultat.Feil).originalFeil shouldBe
                    JournalføringOgDistribueringsResultat.JournalføringOgDistribueringsFeil.Distribuering(
                        KunneIkkeBestilleBrevForDokument.FeilVedBestillingAvBrev,
                    )
            }
            verify(dokumentRepo).hentDokumenterForDistribusjon()
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
        val dokumentService = DistribuerDokumentService(
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
            generertDokument = pdfATom(),
            generertDokumentJson = "{}",
            metadata = Dokument.Metadata(sakId = sakinfo.sakId),
        ),
        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
    )
}
