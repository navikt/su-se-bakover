package no.nav.su.se.bakover.service.brev

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
import no.nav.su.se.bakover.service.dokument.DokumentServiceImpl
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.sakinfo
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock
import java.time.Year
import java.util.UUID

internal class DistribuerBrevServiceTest {

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

        ServiceOgMocks(
            dokDistFordeling = dockdistMock,
        ).dokumentService.distribuerBrev(
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
    fun `journalfør dokument - finner ikke person`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val sakService = mock<SakService> {
            on { hentSakInfo(any()) } doReturn SakInfo(
                sakId = sakId, saksnummer = saksnummer, fnr = fnr, type = Sakstype.UFØRE
            ).right()
        }

        val dokumentdistribusjon = dokumentdistribusjon()

        ServiceOgMocks(
            sakService = sakService,
            personService = personServiceMock,
        ).let {
            it.dokumentService.journalførDokument(dokumentdistribusjon) shouldBe KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left()
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `journalfør dokument - feil ved journalføring`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val sakService = mock<SakService> {
            on { hentSakInfo(any()) } doReturn SakInfo(
                sakId = sakId, saksnummer = saksnummer, fnr = fnr, type = Sakstype.UFØRE
            ).right()
        }

        val dokarkivMock = mock<DokArkiv> {
            on { opprettJournalpost(any()) } doReturn ClientError(500, "kek").left()
        }

        val dokumentdistribusjon = dokumentdistribusjon()

        ServiceOgMocks(
            sakService = sakService,
            personService = personServiceMock,
            dokArkiv = dokarkivMock,
        ).let {
            it.dokumentService.journalførDokument(dokumentdistribusjon) shouldBe KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost.left()
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
            on { hentSakInfo(any()) } doReturn SakInfo(
                sakId = sakId, saksnummer = saksnummer, fnr = fnr, type = Sakstype.UFØRE
            ).right()
        }

        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("done")))

        ServiceOgMocks(
            sakService = sakService,
            personService = personServiceMock,
        ).let {
            it.dokumentService.journalførDokument(dokumentdistribusjon) shouldBe dokumentdistribusjon.right()
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            it.verifyNoMoreInteraction()
        }
    }

    @Test
    fun `journalfør dokument - happy`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val dokarkivMock = mock<DokArkiv> {
            on { opprettJournalpost(any()) } doReturn JournalpostId("happy").right()
        }
        val sakService = mock<SakService> {
            on { hentSakInfo(any()) } doReturn SakInfo(
                sakId = sakId, saksnummer = saksnummer, fnr = fnr, type = Sakstype.UFØRE
            ).right()
        }

        val dokumentRepoMock = mock<DokumentRepo>()

        val dokumentdistribusjon = dokumentdistribusjon()

        val expected = dokumentdistribusjon
            .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("happy")))

        ServiceOgMocks(
            sakService = sakService,
            personService = personServiceMock,
            dokArkiv = dokarkivMock,
            dokumentRepo = dokumentRepoMock,
        ).let {
            it.dokumentService.journalførDokument(dokumentdistribusjon) shouldBe expected.right()
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            verify(dokarkivMock).opprettJournalpost(any())
            verify(dokumentRepoMock).oppdaterDokumentdistribusjon(expected)
            it.verifyNoMoreInteraction()
        }
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

    @Test
    fun `distribuer dokument - happy`() {
        val dokDistMock = mock<DokDistFordeling> {
            on { bestillDistribusjon(any(), any(), any()) } doReturn BrevbestillingId("happy").right()
        }

        val dokumentRepoMock = mock<DokumentRepo>()

        val dokumentdistribusjon = dokumentdistribusjon()
            .copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("very")))

        val expected = dokumentdistribusjon
            .copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    JournalpostId("very"),
                    BrevbestillingId("happy"),
                ),
            )

        ServiceOgMocks(
            dokDistFordeling = dokDistMock,
            dokumentRepo = dokumentRepoMock,
        ).let {
            it.dokumentService.distribuerDokument(dokumentdistribusjon) shouldBe expected.right()
            verify(dokDistMock).bestillDistribusjon(
                JournalpostId("very"),
                Distribusjonstype.VEDTAK,
                distribusjonstidspunkt,
            )
            verify(dokumentRepoMock).oppdaterDokumentdistribusjon(expected)
            it.verifyNoMoreInteraction()
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
            sakService = sakService,
            dokumentRepo = dokumentRepo,
            dokumentSkattRepo = dokumentSkattRepo,
            dokDistFordeling = dokDistFordeling,
            personService = personService,
            dokArkiv = dokArkiv,
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
