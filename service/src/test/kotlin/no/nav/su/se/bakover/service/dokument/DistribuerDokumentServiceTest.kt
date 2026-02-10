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
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.distribuering.DokDistFordeling
import dokument.domain.distribuering.KunneIkkeBestilleDistribusjon
import dokument.domain.hendelser.DokumentHendelseRepo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.domain.backoff.Failures
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.application.consumer.DistribuerDokumentHendelserKonsument
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsFeil
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.minimumPdfAzeroPadded
import no.nav.su.se.bakover.test.nyDistribueringsAdresse
import no.nav.su.se.bakover.test.sakinfo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.Person
import tilgangstyring.application.TilgangstyringService
import java.time.Clock
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
            .copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                    JournalpostId("very"),
                    Failures.EMPTY,
                ),
            )
        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokumenterForDistribusjon() } doReturn listOf(dokumentdistribusjon)
        }
        val dokdistFordeling = mock<DokDistFordeling> {
            on { this.bestillDistribusjon(any(), any(), any(), anyOrNull()) } doReturn BrevbestillingId("id").right()
        }

        ServiceOgMocks(dokumentRepo = dokumentRepo, dokDistFordeling = dokdistFordeling).dokumentService.distribuer()
            .let {
                it.size shouldBe 1
                it.first().shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Ok>()
                it.first().brevbestillingsId shouldBe BrevbestillingId("id")
            }

        verify(dokumentRepo).hentDokumenterForDistribusjon()
        verify(dokdistFordeling).bestillDistribusjon(
            argThat { it shouldBe dokumentdistribusjon.journalføringOgBrevdistribusjon.journalpostId() },
            argThat { it shouldBe Distribusjonstype.VEDTAK },
            argThat { it shouldBe Distribusjonstidspunkt.KJERNETID },
            eq(null),
        )
        verify(dokumentRepo).oppdaterDokumentdistribusjon(
            dokumentdistribusjon.copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    JournalpostId("very"),
                    BrevbestillingId("id"),
                    Failures.EMPTY,
                ),
            ),
        )
    }

    @Test
    fun `distribuerer dokumentet til angitt adresse`() {
        val dokumentdistribusjon = dokumentdistribusjon(distribueringsadresse = nyDistribueringsAdresse())
            .copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                    JournalpostId("very"),
                    Failures.EMPTY,
                ),
            )
        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokumenterForDistribusjon() } doReturn listOf(dokumentdistribusjon)
        }
        val dokdistFordeling = mock<DokDistFordeling> {
            on { this.bestillDistribusjon(any(), any(), any(), anyOrNull()) } doReturn BrevbestillingId("id").right()
        }

        ServiceOgMocks(dokumentRepo = dokumentRepo, dokDistFordeling = dokdistFordeling).dokumentService.distribuer()
            .let {
                it.size shouldBe 1
                it.first().shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Ok>()
                it.first().brevbestillingsId shouldBe BrevbestillingId("id")
            }
        verify(dokumentRepo).hentDokumenterForDistribusjon()
        verify(dokdistFordeling).bestillDistribusjon(
            argThat { it shouldBe dokumentdistribusjon.journalføringOgBrevdistribusjon.journalpostId() },
            argThat { it shouldBe Distribusjonstype.VEDTAK },
            argThat { it shouldBe Distribusjonstidspunkt.KJERNETID },
            argThat { it shouldBe nyDistribueringsAdresse() },
        )
        verify(dokumentRepo).oppdaterDokumentdistribusjon(
            dokumentdistribusjon.copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    JournalpostId("very"),
                    BrevbestillingId("id"),
                    Failures.EMPTY,
                ),
            ),
        )

        verifyNoMoreInteractions(dokumentRepo)
        verifyNoMoreInteractions(dokdistFordeling)
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
                    JournalføringOgDistribueringsFeil.Distribuering(
                        KunneIkkeBestilleBrevForDokument.MåJournalføresFørst,
                    )
            }
            verify(dokumentRepo).hentDokumenterForDistribusjon()
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `distribuer dokument - allerede distribuert`() {
        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    JournalpostId("very"),
                    BrevbestillingId("happy"),
                    Failures.EMPTY,
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
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `distribuer dokument - feil ved bestilling av brev`() {
        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                    JournalpostId("sad"),
                    Failures.EMPTY,
                ),
            )
        val dokumentRepoMock = mock<DokumentRepo> {
            on { hentDokumenterForDistribusjon(any()) } doReturn listOf(dokumentdistribusjon)
        }
        val dokDistMock = mock<DokDistFordeling> {
            on { bestillDistribusjon(any(), any(), any(), anyOrNull()) } doReturn KunneIkkeBestilleDistribusjon.left()
        }

        ServiceOgMocks(
            dokDistFordeling = dokDistMock,
            dokumentRepo = dokumentRepoMock,
        ).let {
            it.dokumentService.distribuer().let {
                it.size shouldBe 1
                it.first().shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Feil>()
                it.first().id shouldBe dokumentdistribusjon.id
                (it.first() as JournalføringOgDistribueringsResultat.Feil).originalFeil shouldBe
                    JournalføringOgDistribueringsFeil.Distribuering(
                        KunneIkkeBestilleBrevForDokument.FeilVedBestillingAvBrev,
                    )
            }
            verify(dokumentRepoMock).hentDokumenterForDistribusjon()
            verify(dokDistMock).bestillDistribusjon(
                JournalpostId("sad"),
                Distribusjonstype.VEDTAK,
                distribusjonstidspunkt,
                null,
            )
            verify(dokumentRepoMock).oppdaterDokumentdistribusjon(
                argThat {
                    it shouldBe dokumentdistribusjon.copy(
                        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
                            journalpostId = JournalpostId("sad"),
                            distribusjonFailures = Failures(count = 1, last = Tidspunkt.now(fixedClock)),
                        ),
                    )
                },
            )
            it.verifyNoMoreInteractions()
        }
    }

    private data class ServiceOgMocks(
        val dokDistFordeling: DokDistFordeling = mock(),
        val dokumentRepo: DokumentRepo = mock(),
        val dokumentHendelseRepo: DokumentHendelseRepo = mock(),
        val distribuerDokumentHendelserKonsument: DistribuerDokumentHendelserKonsument = mock(),
        val tilgangstyringService: TilgangstyringService = mock(),
        val clock: Clock = fixedClock,
    ) {
        val dokumentService = DistribuerDokumentService(
            dokDistFordeling = dokDistFordeling,
            dokumentRepo = dokumentRepo,
            dokumentHendelseRepo = dokumentHendelseRepo,
            distribuerDokumentHendelserKonsument = distribuerDokumentHendelserKonsument,
            tilgangstyringService = tilgangstyringService,
            clock = clock,
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(dokDistFordeling, dokumentRepo)
        }
    }

    private fun dokumentdistribusjon(
        distribueringsadresse: Distribueringsadresse? = null,
    ): Dokumentdistribusjon = Dokumentdistribusjon(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        dokument = Dokument.MedMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = minimumPdfAzeroPadded(),
            generertDokumentJson = "{}",
            metadata = Dokument.Metadata(sakId = sakinfo.sakId),
            distribueringsadresse = distribueringsadresse,
        ),
        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
    )
}
