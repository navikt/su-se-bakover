package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.stubs.person.MicrosoftGraphApiClientStub
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Person.Navn
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.Søknad.Lukket.LukketType.AVVIST
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.søknad.lukk.AvvistSøknadBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

internal class LukkSøknadServiceImplTest {
    private val fixedEpochClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val saksbehandler = NavIdentBruker.Saksbehandler("Z993156")
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val journalførtSøknadMedOppgaveJournalpostId = JournalpostId("journalførtSøknadMedOppgaveJournalpostId")
    private val lukketJournalpostId = JournalpostId("lukketJournalpostId")
    private val brevbestillingId = BrevbestillingId("brevbestillingId")
    private val oppgaveId = OppgaveId("oppgaveId")
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val person = Person(
        ident = Ident(
            fnr = fnr,
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy")
    )
    private val sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        søknader = emptyList(),
        behandlinger = emptyList(),
        utbetalinger = emptyList()
    )
    private val journalførtSøknadMedOppgave = Søknad.Journalført.MedOppgave(
        sakId = sakId,
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.EPOCH,
        søknadInnhold = søknadInnhold,
        journalpostId = journalførtSøknadMedOppgaveJournalpostId,
        oppgaveId = oppgaveId
    )
    private val lukketSøknad = Søknad.Lukket(
        sakId = sakId,
        id = journalførtSøknadMedOppgave.id,
        opprettet = journalførtSøknadMedOppgave.opprettet,
        søknadInnhold = søknadInnhold,
        journalpostId = journalførtSøknadMedOppgave.journalpostId,
        oppgaveId = journalførtSøknadMedOppgave.oppgaveId,
        lukketTidspunkt = Tidspunkt.EPOCH,
        lukketAv = saksbehandler,
        lukketType = Søknad.Lukket.LukketType.TRUKKET,
        lukketJournalpostId = null,
        lukketBrevbestillingId = null
    )

    private val trekkSøknadRequest = LukkSøknadRequest.MedBrev.TrekkSøknad(
        søknadId = journalførtSøknadMedOppgave.id,
        saksbehandler = saksbehandler,
        trukketDato = 1.januar(2020)
    )

    @Test
    fun `fant ikke søknad`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn null
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = mock(),
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.FantIkkeSøknad.left()

        verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `fant ikke person`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = mock(),
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.FantIkkePerson.left()

        inOrder(
            søknadRepoMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `trekker en søknad uten mangler`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val observerMock: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }

        val actual = LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = MicrosoftGraphApiClientStub,
            clock = fixedEpochClock,
        ).apply { addObserver(observerMock) }.lukkSøknad(trekkSøknadRequest).getOrHandle { throw RuntimeException("Feil i test") }
        actual shouldBe LukketSøknad.UtenMangler(sak, actual.søknad)

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock, observerMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })

            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        person = person,
                        søknad = lukketSøknad.copy(
                            journalpostId = journalførtSøknadMedOppgaveJournalpostId,
                            oppgaveId = oppgaveId,
                        ),
                        trukketDato = 1.januar(2020),
                        saksbehandlerNavn = "Testbruker, Lokal"
                    )
                },
                saksnummer = argThat { it shouldBe saksnummer }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        oppgaveId = oppgaveId,
                        journalpostId = journalførtSøknadMedOppgaveJournalpostId,
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = brevbestillingId
                    )
                }
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
            verify(observerMock).handle(argThat { it shouldBe Event.Statistikk.SøknadStatistikk.SøknadLukket(journalførtSøknadMedOppgave, sak.saksnummer) })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock, observerMock)
    }

    @Test
    fun `lukker avvist søknad uten brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val resultat = LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = mock(),
            clock = fixedEpochClock,
        ).lukkSøknad(
            LukkSøknadRequest.UtenBrev.AvvistSøknad(
                søknadId = journalførtSøknadMedOppgave.id,
                saksbehandler = saksbehandler
            )
        ).getOrHandle { throw RuntimeException("Feil i test") }

        resultat shouldBe LukketSøknad.UtenMangler(sak, resultat.søknad)

        inOrder(
            søknadRepoMock, sakServiceMock, oppgaveServiceMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        lukketType = AVVIST,
                    )
                },
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `lukker avvist søknad med brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val actual = LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = MicrosoftGraphApiClientStub,
            clock = fixedEpochClock,
        ).lukkSøknad(
            LukkSøknadRequest.MedBrev.AvvistSøknad(
                søknadId = journalførtSøknadMedOppgave.id,
                saksbehandler = saksbehandler,
                brevConfig = BrevConfig.Fritekst("Fritekst")
            )
        ).getOrHandle { throw RuntimeException("feil i test") }

        actual shouldBe LukketSøknad.UtenMangler(sak, actual.søknad)

        inOrder(
            søknadRepoMock, sakServiceMock, oppgaveServiceMock, brevServiceMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe AvvistSøknadBrevRequest(
                        person = person,
                        saksbehandlerNavn = "Testbruker, Lokal",
                        brevConfig = BrevConfig.Fritekst("Fritekst")
                    )
                },
                argThat { it shouldBe saksnummer }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        journalpostId = journalførtSøknadMedOppgaveJournalpostId,
                        oppgaveId = oppgaveId,
                        lukketType = AVVIST,
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = brevbestillingId
                    )
                },
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `kan ikke sette lukketDato tidligere enn da søknadeden var opprettet`() {

        val treDagerGammelSøknad = Søknad.Ny(
            sakId = sakId,
            id = UUID.randomUUID(),
            opprettet = LocalDateTime.now().minusDays(3).toTidspunkt(zoneIdOslo),
            søknadInnhold = søknadInnhold,
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn treDagerGammelSøknad
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = mock(),
            clock = fixedEpochClock,
        ).lukkSøknad(
            LukkSøknadRequest.MedBrev.TrekkSøknad(
                søknadId = treDagerGammelSøknad.id,
                saksbehandler = saksbehandler,
                trukketDato = LocalDate.now().minusDays(4)
            )
        ) shouldBe KunneIkkeLukkeSøknad.UgyldigTrukketDato.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe treDagerGammelSøknad.id })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `en søknad med behandling skal ikke bli trukket`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }
        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = mock(),
            clock = fixedEpochClock,
        ).lukkSøknad(
            LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                søknadId = journalførtSøknadMedOppgave.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `en allerede trukket søknad skal ikke bli trukket`() {
        val trukketSøknad = lukketSøknad.copy()
        val trekkSøknadRequest = trekkSøknadRequest.copy(
            søknadId = trukketSøknad.id
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn trukketSøknad
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService>()

        val brevServiceMock = mock<BrevService>()
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val oppgaveServiceMock = mock<OppgaveService>()
        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = mock(),
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()

        inOrder(søknadRepoMock, personServiceMock) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe trukketSøknad.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe trukketSøknad.id })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `lager brevutkast`() {
        val pdf = "".toByteArray()
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn pdf.right()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = MicrosoftGraphApiClientStub,
            clock = fixedEpochClock,
        ).lagBrevutkast(trekkSøknadRequest) shouldBe pdf.right()

        inOrder(
            søknadRepoMock, brevServiceMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })

            verify(brevServiceMock).lagBrev(
                argThat {
                    it shouldBe TrukketSøknadBrevRequest(person, journalførtSøknadMedOppgave, 1.januar(2020), "Testbruker, Lokal")
                }
            )
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `lager brevutkast finner ikke person`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService>()

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = mock(),
            clock = fixedEpochClock,
        ).lagBrevutkast(trekkSøknadRequest) shouldBe KunneIkkeLageBrevutkast.FantIkkePerson.left()

        inOrder(
            søknadRepoMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `lager brevutkast finner ikke søknad`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn null
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = mock(),
            clock = fixedEpochClock,
        ).lagBrevutkast(trekkSøknadRequest) shouldBe KunneIkkeLageBrevutkast.FantIkkeSøknad.left()

        inOrder(
            søknadRepoMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `lager brevutkast klarer ikke lage brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }
        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = MicrosoftGraphApiClientStub,
            clock = fixedEpochClock,
        ).lagBrevutkast(trekkSøknadRequest) shouldBe KunneIkkeLageBrevutkast.KunneIkkeLageBrev.left()

        inOrder(
            søknadRepoMock, brevServiceMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })

            verify(brevServiceMock).lagBrev(
                argThat {
                    it shouldBe TrukketSøknadBrevRequest(person, journalførtSøknadMedOppgave, 1.januar(2020), "Testbruker, Lokal")
                }
            )
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `svarer med ukjentBrevType når det ikke skal lages brev`() {

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
        }
        val sakServiceMock = mock<SakService>()

        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService>()

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }
        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = mock(),
            clock = fixedEpochClock,
        ).lagBrevutkast(
            LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                søknadId = journalførtSøknadMedOppgave.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe KunneIkkeLageBrevutkast.UkjentBrevtype.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `Kan ikke sende brevbestilling og kan ikke lukke oppgave`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val actual = LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = MicrosoftGraphApiClientStub,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest).getOrHandle { throw RuntimeException("Feil i test") }

        actual shouldBe LukketSøknad.MedMangler.KunneIkkeDistribuereBrev(sak, actual.søknad)

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        person = person,
                        søknad = lukketSøknad.copy(
                            lukketJournalpostId = null,
                            journalpostId = journalførtSøknadMedOppgaveJournalpostId,
                            oppgaveId = oppgaveId
                        ),
                        trukketDato = 1.januar(2020),
                        saksbehandlerNavn = "Testbruker, Lokal"
                    )
                },
                argThat { it shouldBe saksnummer }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = null,
                        journalpostId = journalførtSøknadMedOppgaveJournalpostId,
                        oppgaveId = oppgaveId
                    )
                }
            )

            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `skal ikke kunne lukke journalført søknad uten oppgave`() {
        val søknad = Søknad.Journalført.UtenOppgave(
            sakId = journalførtSøknadMedOppgave.sakId,
            id = journalførtSøknadMedOppgave.id,
            opprettet = journalførtSøknadMedOppgave.opprettet,
            søknadInnhold = journalførtSøknadMedOppgave.søknadInnhold,
            journalpostId = journalførtSøknadMedOppgaveJournalpostId,
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn søknad
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(lukketJournalpostId) } doReturn brevbestillingId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }
        val actual = LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = mock(),
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest)

        actual shouldBe KunneIkkeLukkeSøknad.SøknadManglerOppgave.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknad.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe søknad.id })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `skal ikke kunne lukke søknad som mangler journalpost og oppgave`() {
        val nySøknad = Søknad.Ny(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = søknadInnhold,
        )
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService>()

        val personServiceMock = mock<PersonService>()
        val actual = LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = mock(),
            clock = fixedEpochClock,
        ).lukkSøknad(
            trekkSøknadRequest.copy(
                søknadId = nySøknad.id
            )
        )

        actual shouldBe KunneIkkeLukkeSøknad.SøknadManglerOppgave.left()

        inOrder(
            søknadRepoMock
        ) {
            verify(søknadRepoMock).hentSøknad(
                argThat {
                    it shouldBe nySøknad.id
                }
            )
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe nySøknad.id })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `Kan ikke lukke oppgave`() {

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val actual = LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = MicrosoftGraphApiClientStub,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest).getOrHandle { throw RuntimeException("Feil i test") }

        actual shouldBe LukketSøknad.MedMangler.KunneIkkeLukkeOppgave(sak, actual.søknad)

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })

            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        person = person,
                        søknad = lukketSøknad.copy(
                            journalpostId = journalførtSøknadMedOppgaveJournalpostId,
                            oppgaveId = oppgaveId,
                        ),
                        trukketDato = 1.januar(2020),
                        "Testbruker, Lokal"
                    )
                },
                saksnummer = argThat { it shouldBe saksnummer }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        oppgaveId = oppgaveId,
                        journalpostId = journalførtSøknadMedOppgaveJournalpostId,
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = brevbestillingId
                    )
                }
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }

    @Test
    fun `Kan ikke journalføre`() {

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }
        val oppgaveServiceMock = mock<OppgaveService>()

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = MicrosoftGraphApiClientStub,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.KunneIkkeJournalføreBrev.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })

            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        person = person,
                        søknad = lukketSøknad.copy(
                            journalpostId = journalførtSøknadMedOppgaveJournalpostId,
                            oppgaveId = oppgaveId,
                        ),
                        trukketDato = 1.januar(2020),
                        "Testbruker, Lokal"
                    )
                },
                saksnummer = argThat { it shouldBe sak.saksnummer }
            )
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personServiceMock)
    }
}
