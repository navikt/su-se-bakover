package no.nav.su.se.bakover.service.dokument

import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.brev.KunneIkkeJournalføreDokument
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
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
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import person.domain.PersonService
import java.time.Year
import java.util.UUID

class JournalførDokumentServiceTest {
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

    @Test
    fun `journalfør dokument - finner ikke person`() {
        val dokumentdistribusjon = dokumentdistribusjon()
        val personServiceMock =
            mock<PersonService> { on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left() }
        val sakService = mock<SakService> {
            on { hentSakInfo(any()) } doReturn SakInfo(sakId, saksnummer, fnr, Sakstype.UFØRE).right()
        }
        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokumenterForJournalføring(any()) } doReturn listOf(dokumentdistribusjon)
        }
        ServiceOgMocks(sakService = sakService, personService = personServiceMock, dokumentRepo = dokumentRepo).let {
            it.journalførDokumentService.journalfør().let {
                it.size shouldBe 1
                it.first().shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Feil>()
                it.first().id shouldBe dokumentdistribusjon.id
                it.first().journalpostId shouldBe null
                it.first().brevbestillingsId shouldBe null
                (it.first() as JournalføringOgDistribueringsResultat.Feil).originalFeil shouldBe JournalføringOgDistribueringsResultat.JournalføringOgDistribueringsFeil.Journalføring(
                    KunneIkkeJournalføreDokument.KunneIkkeFinnePerson,
                )
            }
            verify(sakService).hentSakInfo(argThat { it shouldBe sakId })
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
        }
    }

    @Test
    fun `journalfør dokument - feil ved journalføring`() {
        val dokumentdistribusjon = dokumentdistribusjon()
        val dokumentRepo = mock<DokumentRepo> {
            on { hentDokumenterForJournalføring(any()) } doReturn listOf(dokumentdistribusjon)
        }
        val personServiceMock =
            mock<PersonService> { on { hentPersonMedSystembruker(any()) } doReturn person.right() }
        val dokarkivMock =
            mock<DokArkiv> { on { opprettJournalpost(any()) } doReturn ClientError(500, "kek").left() }
        val sakService = mock<SakService> {
            on { hentSakInfo(any()) } doReturn SakInfo(sakId, saksnummer, fnr, Sakstype.UFØRE).right()
        }

        ServiceOgMocks(
            sakService = sakService,
            personService = personServiceMock,
            dokArkiv = dokarkivMock,
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
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            verify(dokarkivMock).opprettJournalpost(any())
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
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }
        val sakService = mock<SakService> {
            on { hentSakInfo(any()) } doReturn SakInfo(sakId, saksnummer, fnr, Sakstype.UFØRE).right()
        }

        ServiceOgMocks(sakService = sakService, personService = personServiceMock, dokumentRepo = dokumentRepo).let {
            it.journalførDokumentService.journalfør().let {
                it.size shouldBe 1
                it.first().shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Ok>()
                it.first().id shouldBe dokumentdistribusjon.id
                it.first().journalpostId shouldBe dokumentdistribusjon.journalføringOgBrevdistribusjon.journalpostId()
                it.first().brevbestillingsId shouldBe dokumentdistribusjon.journalføringOgBrevdistribusjon.brevbestillingsId()
            }
            verify(dokumentRepo).hentDokumenterForJournalføring()
            verify(sakService).hentSakInfo(argThat { it shouldBe sakId })
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            it.verifyNoMoreInteraction()
        }
    }

    private data class ServiceOgMocks(
        val dokArkiv: DokArkiv = mock(),
        val dokumentRepo: DokumentRepo = mock(),
        val dokumentSkattRepo: DokumentSkattRepo = mock(),
        val sakService: SakService = mock(),
        val personService: PersonService = mock(),
    ) {
        val journalførDokumentService = JournalførDokumentService(
            dokumentRepo = dokumentRepo,
            dokArkiv = dokArkiv,
            sakService = sakService,
            personService = personService,
        )

        fun verifyNoMoreInteraction() {
            verifyNoMoreInteractions(dokArkiv, dokumentRepo, sakService, personService)
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
