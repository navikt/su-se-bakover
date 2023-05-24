package no.nav.su.se.bakover.service.dokument

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.dokdistfordeling.KunneIkkeBestilleDistribusjon
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.Distribusjonstidspunkt
import no.nav.su.se.bakover.domain.brev.Distribusjonstype
import no.nav.su.se.bakover.domain.brev.KunneIkkeBestilleBrevForDokument
import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.sakinfo
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.skatt.nySkattedokumentGenerert
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock
import java.time.Year
import java.util.UUID

internal class DokumentServiceTest {

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
    val distribusjonstype = Distribusjonstype.VIKTIG
    val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID

    @Nested
    inner class Journalføring {
        @Test
        fun `journalfører dokumenter`() {
            val dokumentDis = dokumentdistribusjon()
            val skattDokument = nySkattedokumentGenerert(sakId = sakinfo.sakId)

            val sakService = mock<SakService> {
                on { hentSakInfo(any()) } doReturn sakinfo.right()
            }
            val personService = mock<PersonService> {
                on { hentPersonMedSystembruker(any()) } doReturn person.right()
            }
            val dokumentRepo = mock<DokumentRepo> {
                on { this.hentDokumenterForJournalføring() } doReturn listOf(dokumentDis)
            }
            val dokumentSkattRepo = mock<DokumentSkattRepo> {
                on { this.hentDokumenterForJournalføring() } doReturn listOf(skattDokument)
            }
            val dokarkiv = mock<DokArkiv> {
                on { opprettJournalpost(any()) } doReturn JournalpostId("1").right()
            }

            val serviceAndMocks = ServiceOgMocks(
                sakService = sakService,
                personService = personService,
                dokumentRepo = dokumentRepo,
                dokumentSkattRepo = dokumentSkattRepo,
                dokArkiv = dokarkiv,
            )
            serviceAndMocks.dokumentService.journalførDokumenter()
            verify(dokumentRepo, times(1)).hentDokumenterForJournalføring()
            verify(dokumentSkattRepo, times(1)).hentDokumenterForJournalføring()
            verify(sakService, times(2)).hentSakInfo(argThat { it shouldBe sakinfo.sakId })
            verify(personService, times(2)).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            verify(dokumentRepo, times(1)).oppdaterDokumentdistribusjon(
                argThat {
                    it shouldBe dokumentDis.copy(
                        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("1")),
                    )
                },
            )
            verify(dokumentSkattRepo, times(1)).lagre(
                argThat { it shouldBe Skattedokument.Journalført(skattDokument, JournalpostId("1")) },
            )
            verifyNoMoreInteractions(sakService, personService, dokumentRepo, dokumentSkattRepo)
        }

        @Test
        fun `journalfør dokument - finner ikke person`() {
            val personServiceMock =
                mock<PersonService> { on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left() }
            val sakService = mock<SakService> {
                on { hentSakInfo(any()) } doReturn SakInfo(sakId, saksnummer, fnr, Sakstype.UFØRE).right()
            }

            ServiceOgMocks(sakService = sakService, personService = personServiceMock).let {
                it.dokumentService.journalførDokument(dokumentdistribusjon()) shouldBe KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left()
                verify(sakService).hentSakInfo(argThat { it shouldBe sakId })
                verify(personServiceMock).hentPersonMedSystembruker(fnr)
            }
        }

        @Test
        fun `journalfør dokument - feil ved journalføring`() {
            val personServiceMock =
                mock<PersonService> { on { hentPersonMedSystembruker(any()) } doReturn person.right() }
            val dokarkivMock =
                mock<DokArkiv> { on { opprettJournalpost(any()) } doReturn ClientError(500, "kek").left() }
            val sakService = mock<SakService> {
                on { hentSakInfo(any()) } doReturn SakInfo(sakId, saksnummer, fnr, Sakstype.UFØRE).right()
            }

            ServiceOgMocks(sakService = sakService, personService = personServiceMock, dokArkiv = dokarkivMock).let {
                it.dokumentService.journalførDokument(dokumentdistribusjon()) shouldBe KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost.left()
                verify(sakService).hentSakInfo(argThat { it shouldBe sakId })
                verify(personServiceMock).hentPersonMedSystembruker(fnr)
                verify(dokarkivMock).opprettJournalpost(any())
                it.verifyNoMoreInteraction()
            }
        }

        @Test
        fun `journalfør dokument - dokument allerede journalført`() {
            val personServiceMock = mock<PersonService> {
                on { hentPersonMedSystembruker(any()) } doReturn person.right()
            }
            val sakService = mock<SakService> {
                on { hentSakInfo(any()) } doReturn SakInfo(sakId, saksnummer, fnr, Sakstype.UFØRE).right()
            }

            val dokumentdistribusjon = dokumentdistribusjon()
                .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("done")))

            ServiceOgMocks(sakService = sakService, personService = personServiceMock).let {
                it.dokumentService.journalførDokument(dokumentdistribusjon) shouldBe dokumentdistribusjon.right()
                verify(sakService).hentSakInfo(argThat { it shouldBe sakId })
                verify(personServiceMock).hentPersonMedSystembruker(fnr)
                it.verifyNoMoreInteraction()
            }
        }
    }

    @Nested
    inner class Distribuering {
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
    }

    private data class ServiceOgMocks(
        val dokArkiv: DokArkiv = mock(),
        val dokDistFordeling: DokDistFordeling = mock(),
        val dokumentRepo: DokumentRepo = mock(),
        val dokumentSkattRepo: DokumentSkattRepo = mock(),
        val sakService: SakService = mock(),
        val personService: PersonService = mock(),
        val clock: Clock = mock(),
        val sakInfo: SakInfo = sakinfo,
    ) {
        val dokumentService = DokumentServiceImpl(
            dokDistFordeling = dokDistFordeling,
            dokumentRepo = dokumentRepo,
        )

        fun verifyNoMoreInteraction() {
            verifyNoMoreInteractions(
                dokArkiv,
                dokDistFordeling,
                dokumentRepo,
                sakService,
                personService,
            )
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
