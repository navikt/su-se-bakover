package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

class OpprettManglendeJournalpostOgOppgaveTest {
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val sakId = UUID.randomUUID()
    private val nySøknad = Søknad.Ny(
        sakId = sakId,
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.EPOCH,
        søknadInnhold = søknadInnhold,
    )
    private val fnr = Fnr.generer()
    private val journalførtSøknad = nySøknad.journalfør(JournalpostId("1"))
    private val sak = Sak(
        id = sakId,
        saksnummer = Saksnummer(2021),
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        søknader = emptyList(),
        søknadsbehandlinger = emptyList(),
        utbetalinger = emptyList()
    )
    private val person = Person(
        ident = Ident(
            fnr = fnr,
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy")
    )

    @Test
    fun `ingen søknader`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknaderUtenJournalpost() } doReturn emptyList()
            on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn emptyList()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = mock(),
            sakFactory = mock(),
            pdfGenerator = mock(),
            dokArkiv = mock(),
            personService = mock(),
            oppgaveService = mock(),
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.opprettManglendeJournalpostOgOppgave()
        actual shouldBe OpprettManglendeJournalpostOgOppgaveResultat(emptyList(), emptyList())
        inOrder(
            søknadRepoMock
        ) {
            verify(søknadRepoMock).hentSøknaderUtenJournalpost()
            verify(søknadRepoMock).hentSøknaderMedJournalpostMenUtenOppgave()
        }
        verifyNoMoreInteractions(søknadRepoMock)
    }

    @Test
    fun `finner ikke saken`() {
        val journalførtSøknad = nySøknad.journalfør(JournalpostId("1"))

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknaderUtenJournalpost() } doReturn listOf(nySøknad)
            on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn listOf(journalførtSøknad)
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn FantIkkeSak.left()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = mock(),
            pdfGenerator = mock(),
            dokArkiv = mock(),
            personService = mock(),
            oppgaveService = mock(),
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.opprettManglendeJournalpostOgOppgave()
        actual shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
            journalpostResultat = listOf(KunneIkkeOppretteJournalpost(sakId, nySøknad.id, "Fant ikke sak").left()),
            oppgaveResultat = listOf(KunneIkkeOppretteOppgave(sakId, nySøknad.id, journalførtSøknad.journalpostId, "Fant ikke sak").left())
        )

        inOrder(
            sakServiceMock,
            søknadRepoMock
        ) {
            verify(søknadRepoMock).hentSøknaderUtenJournalpost()
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(søknadRepoMock).hentSøknaderMedJournalpostMenUtenOppgave()
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock)
    }

    @Test
    fun `finner ikke person`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknaderUtenJournalpost() } doReturn listOf(nySøknad)
            on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn listOf(journalførtSøknad)
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(fnr) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = mock(),
            pdfGenerator = mock(),
            dokArkiv = mock(),
            personService = personServiceMock,
            oppgaveService = mock(),
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.opprettManglendeJournalpostOgOppgave()
        actual shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
            journalpostResultat = listOf(KunneIkkeOppretteJournalpost(sakId, nySøknad.id, "Fant ikke person").left()),
            oppgaveResultat = listOf(KunneIkkeOppretteOppgave(sakId, journalførtSøknad.id, journalførtSøknad.journalpostId, "Fant ikke person").left())
        )
        inOrder(
            personServiceMock,
            sakServiceMock,
            søknadRepoMock
        ) {
            verify(søknadRepoMock).hentSøknaderUtenJournalpost()
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            verify(søknadRepoMock).hentSøknaderMedJournalpostMenUtenOppgave()
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
        }
        verifyNoMoreInteractions(personServiceMock, sakServiceMock, søknadRepoMock)
    }

    @Test
    fun `opprett journalpost feiler`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknaderUtenJournalpost() } doReturn listOf(nySøknad)
            on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn emptyList()
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(fnr) } doReturn person.right()
        }

        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn ClientError(0, "").left()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = mock(),
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = mock(),
            personService = personServiceMock,
            oppgaveService = mock(),
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.opprettManglendeJournalpostOgOppgave()
        actual shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
            journalpostResultat = listOf(KunneIkkeOppretteJournalpost(sakId, nySøknad.id, "Kunne ikke generere PDF").left()),
            oppgaveResultat = emptyList()
        )
        inOrder(
            personServiceMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock
        ) {
            verify(søknadRepoMock).hentSøknaderUtenJournalpost()
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold.create(
                        saksnummer = sak.saksnummer,
                        søknadsId = it.søknadsId,
                        navn = person.navn,
                        søknadOpprettet = nySøknad.opprettet,
                        søknadInnhold = søknadInnhold,
                        clock = fixedClock,
                    )
                }
            )
            verify(søknadRepoMock).hentSøknaderMedJournalpostMenUtenOppgave()
        }
        verifyNoMoreInteractions(personServiceMock, sakServiceMock, søknadRepoMock, pdfGeneratorMock)
    }

    @Test
    fun `opprett oppgave feiler`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknaderUtenJournalpost() } doReturn emptyList()
            on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn listOf(journalførtSøknad)
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(fnr) } doReturn person.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(any()) } doReturn no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave.left()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = mock(),
            pdfGenerator = mock(),
            dokArkiv = mock(),
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.opprettManglendeJournalpostOgOppgave()
        actual shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
            journalpostResultat = emptyList(),
            oppgaveResultat = listOf(KunneIkkeOppretteOppgave(sakId, journalførtSøknad.id, journalførtSøknad.journalpostId, "Kunne ikke opprette oppgave").left())
        )

        inOrder(
            personServiceMock,
            sakServiceMock,
            søknadRepoMock,
            oppgaveServiceMock
        ) {
            verify(søknadRepoMock).hentSøknaderUtenJournalpost()
            verify(søknadRepoMock).hentSøknaderMedJournalpostMenUtenOppgave()
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
                argThat {
                    it shouldBe OppgaveConfig.Søknad(
                        journalpostId = journalførtSøknad.journalpostId,
                        søknadId = journalførtSøknad.id,
                        aktørId = person.ident.aktørId,
                        clock = fixedClock,
                        tilordnetRessurs = null,
                    )
                },
            )
        }
        verifyNoMoreInteractions(personServiceMock, sakServiceMock, søknadRepoMock, oppgaveServiceMock)
    }

    @Test
    fun `happy case`() {
        val oppgaveId = OppgaveId("1")
        val pdf = "pdf-data".toByteArray()

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknaderUtenJournalpost() } doReturn listOf(nySøknad)
            on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn listOf(journalførtSøknad)
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(fnr) } doReturn person.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(any()) } doReturn oppgaveId.right()
        }

        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
        }

        val dokArkivMock: DokArkiv = mock {
            on { opprettJournalpost(any()) } doReturn journalførtSøknad.journalpostId.right()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = mock(),
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.opprettManglendeJournalpostOgOppgave()
        actual shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
            journalpostResultat = listOf(journalførtSøknad.right()),
            oppgaveResultat = listOf(journalførtSøknad.medOppgave(oppgaveId).right())
        )

        inOrder(
            personServiceMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        ) {
            verify(søknadRepoMock).hentSøknaderUtenJournalpost()
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold.create(
                        saksnummer = sak.saksnummer,
                        søknadsId = it.søknadsId,
                        navn = person.navn,
                        søknadOpprettet = nySøknad.opprettet,
                        søknadInnhold = søknadInnhold,
                        clock = fixedClock,
                    )
                }
            )
            verify(dokArkivMock).opprettJournalpost(
                argThat {
                    it shouldBe Journalpost.Søknadspost.from(
                        person = person,
                        saksnummer = Saksnummer(2021),
                        søknadInnhold = søknadInnhold,
                        pdf = pdf,
                    )
                },
            )
            verify(søknadRepoMock).oppdaterjournalpostId(argThat { journalførtSøknad.id })
            verify(søknadRepoMock).hentSøknaderMedJournalpostMenUtenOppgave()
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
                argThat {
                    it shouldBe OppgaveConfig.Søknad(
                        journalpostId = journalførtSøknad.journalpostId,
                        søknadId = journalførtSøknad.id,
                        aktørId = person.ident.aktørId,
                        clock = fixedClock,
                        tilordnetRessurs = null,
                    )
                },
            )
            verify(søknadRepoMock).oppdaterOppgaveId(
                argThat {
                    it shouldBe Søknad.Journalført.MedOppgave.IkkeLukket(
                        id = journalførtSøknad.id,
                        opprettet = journalførtSøknad.opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                        journalpostId = journalførtSøknad.journalpostId,
                        oppgaveId = oppgaveId
                    )
                }
            )
        }
        verifyNoMoreInteractions(personServiceMock, sakServiceMock, søknadRepoMock, pdfGeneratorMock, dokArkivMock, oppgaveServiceMock)
    }
}
