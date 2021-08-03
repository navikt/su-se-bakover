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
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
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
import no.nav.su.se.bakover.domain.brev.søknad.lukk.AvvistSøknadBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

private val fixedEpochClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

internal class LukkSøknadServiceImplTest {
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val saksbehandler = NavIdentBruker.Saksbehandler("Z993156")
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val journalførtSøknadMedOppgaveJournalpostId = JournalpostId("journalførtSøknadMedOppgaveJournalpostId")
    private val oppgaveId = OppgaveId("oppgaveId")
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val generertPdf = "".toByteArray()
    private val person = Person(
        ident = Ident(
            fnr = fnr,
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
    )
    private val sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        søknader = emptyList(),
        behandlinger = emptyList(),
        utbetalinger = emptyList(),
    )
    private val journalførtSøknadMedOppgave = Søknad.Journalført.MedOppgave(
        sakId = sakId,
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.EPOCH,
        søknadInnhold = søknadInnhold,
        journalpostId = journalførtSøknadMedOppgaveJournalpostId,
        oppgaveId = oppgaveId,
    )
    private val lukketSøknad = Søknad.Lukket(
        id = journalførtSøknadMedOppgave.id,
        opprettet = journalførtSøknadMedOppgave.opprettet,
        sakId = sakId,
        søknadInnhold = søknadInnhold,
        journalpostId = journalførtSøknadMedOppgave.journalpostId,
        oppgaveId = journalførtSøknadMedOppgave.oppgaveId,
        lukketTidspunkt = Tidspunkt.EPOCH,
        lukketAv = saksbehandler,
        lukketType = Søknad.Lukket.LukketType.TRUKKET,
    )

    private val trekkSøknadRequest = LukkSøknadRequest.MedBrev.TrekkSøknad(
        søknadId = journalførtSøknadMedOppgave.id,
        saksbehandler = saksbehandler,
        trukketDato = 1.januar(2020),
    )

    @Test
    fun `fant ikke søknad`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn null
        }
        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
        ).let {
            it.lukkSøknadService.lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `fant ikke person`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
            personService = personServiceMock,
        ).let {
            it.lukkSøknadService.lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.FantIkkePerson.left()

            inOrder(
                søknadRepoMock,
                personServiceMock,
            ) {
                verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `trekker en søknad uten mangler`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn generertPdf.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val observerMock: EventObserver = mock()

        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = MicrosoftGraphApiClientStub,
        ).let {
            val actual = it.lukkSøknadService.apply { addObserver(observerMock) }.lukkSøknad(trekkSøknadRequest)
                .getOrHandle { fail { "Skulle gått bra" } }

            actual shouldBe sak

            inOrder(
                søknadRepoMock,
                sakServiceMock,
                brevServiceMock,
                oppgaveServiceMock,
                personServiceMock,
                it.brevService,
                observerMock,
            ) {
                verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
                val expectedRequest = TrukketSøknadBrevRequest(
                    person = person,
                    søknad = journalførtSøknadMedOppgave,
                    trukketDato = 1.januar(2020),
                    saksbehandlerNavn = "Testbruker, Lokal",
                )
                verify(brevServiceMock).lagBrev(expectedRequest)
                verify(søknadRepoMock).oppdaterSøknad(lukketSøknad)
                verify(it.brevService).lagreDokument(
                    argThat { dokument ->
                        dokument should beOfType<Dokument.MedMetadata.Informasjon>()
                        dokument.tittel shouldBe expectedRequest.brevInnhold.brevTemplate.tittel()
                        dokument.generertDokument shouldBe generertPdf
                        dokument.generertDokumentJson shouldBe expectedRequest.brevInnhold.toJson()
                        dokument.metadata shouldBe Dokument.Metadata(
                            sakId = sakId,
                            søknadId = journalførtSøknadMedOppgave.id,
                            vedtakId = null,
                            bestillBrev = true,
                        )
                    },
                )
                verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
                verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
                verify(observerMock).handle(
                    argThat {
                        it shouldBe Event.Statistikk.SøknadStatistikk.SøknadLukket(
                            lukketSøknad,
                            sak.saksnummer,
                        )
                    },
                )
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lukker avvist søknad uten brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
        ).let {
            val resultat = it.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.UtenBrev.AvvistSøknad(
                    søknadId = journalførtSøknadMedOppgave.id,
                    saksbehandler = saksbehandler,
                ),
            ).getOrHandle { fail { "Skulle gått bra" } }

            resultat shouldBe sak

            inOrder(
                søknadRepoMock,
                sakServiceMock,
                oppgaveServiceMock,
                personServiceMock,
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
                verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
                verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `lukker avvist søknad med brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn generertPdf.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = MicrosoftGraphApiClientStub,
        ).let {
            val actual = it.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.MedBrev.AvvistSøknad(
                    søknadId = journalførtSøknadMedOppgave.id,
                    saksbehandler = saksbehandler,
                    brevConfig = BrevConfig.Fritekst("Fritekst"),
                ),
            ).getOrHandle { fail { "Skulle gått bra" } }

            actual shouldBe sak

            inOrder(
                søknadRepoMock,
                sakServiceMock,
                oppgaveServiceMock,
                brevServiceMock,
                personServiceMock,
                it.brevService,
            ) {
                verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
                val expectedRequest = AvvistSøknadBrevRequest(
                    person = person,
                    brevConfig = BrevConfig.Fritekst(
                        fritekst = "Fritekst",
                    ),
                    saksbehandlerNavn = "Testbruker, Lokal",
                )
                verify(brevServiceMock).lagBrev(expectedRequest)
                verify(søknadRepoMock).oppdaterSøknad(
                    argThat {
                        it shouldBe lukketSøknad.copy(
                            journalpostId = journalførtSøknadMedOppgaveJournalpostId,
                            oppgaveId = oppgaveId,
                            lukketType = AVVIST,
                        )
                    },
                )
                verify(it.brevService).lagreDokument(
                    argThat { dokument ->
                        dokument should beOfType<Dokument.MedMetadata.Informasjon>()
                        dokument.tittel shouldBe expectedRequest.brevInnhold.brevTemplate.tittel()
                        dokument.generertDokument shouldBe generertPdf
                        dokument.generertDokumentJson shouldBe expectedRequest.brevInnhold.toJson()
                        dokument.metadata shouldBe Dokument.Metadata(
                            sakId = sakId,
                            søknadId = journalførtSøknadMedOppgave.id,
                            vedtakId = null,
                            bestillBrev = true,
                        )
                    },
                )
                verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
                verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            }
            verifyNoMoreInteractions(
                søknadRepoMock,
                sakServiceMock,
                brevServiceMock,
                oppgaveServiceMock,
                personServiceMock,
            )
        }
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
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }

        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
        ).let {
            it.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = treDagerGammelSøknad.id,
                    saksbehandler = saksbehandler,
                    trukketDato = LocalDate.now().minusDays(4),
                ),
            ) shouldBe KunneIkkeLukkeSøknad.UgyldigTrukketDato.left()

            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe treDagerGammelSøknad.id })
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `en søknad med behandling skal ikke bli trukket`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }
        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
        ).let {
            it.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                    søknadId = journalførtSøknadMedOppgave.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()

            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `en allerede trukket søknad skal ikke bli trukket`() {
        val trukketSøknad = lukketSøknad.copy()
        val trekkSøknadRequest = trekkSøknadRequest.copy(
            søknadId = trukketSøknad.id,
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn trukketSøknad
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }

        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
        ).let {
            it.lukkSøknadService.lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()

            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe trukketSøknad.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe trukketSøknad.id })
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lager brevutkast`() {
        val pdf = "".toByteArray()
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn pdf.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
            brevService = brevServiceMock,
            personService = personServiceMock,
        ).let {
            it.lukkSøknadService.lagBrevutkast(trekkSøknadRequest) shouldBe pdf.right()

            inOrder(
                søknadRepoMock,
                brevServiceMock,
                personServiceMock,
            ) {
                verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
                verify(brevServiceMock).lagBrev(
                    argThat {
                        it shouldBe TrukketSøknadBrevRequest(
                            person,
                            journalførtSøknadMedOppgave,
                            1.januar(2020),
                            "Testbruker, Lokal",
                        )
                    },
                )
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `lager brevutkast finner ikke person`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
            personService = personServiceMock,
        ).let {
            it.lukkSøknadService.lagBrevutkast(trekkSøknadRequest) shouldBe KunneIkkeLageBrevutkast.FantIkkePerson.left()

            inOrder(
                søknadRepoMock,
                personServiceMock,
            ) {
                verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `lager brevutkast finner ikke søknad`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn null
        }
        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
        ).let {
            it.lukkSøknadService.lagBrevutkast(trekkSøknadRequest) shouldBe KunneIkkeLageBrevutkast.FantIkkeSøknad.left()

            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lager brevutkast klarer ikke lage brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }
        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
            brevService = brevServiceMock,
            personService = personServiceMock,
        ).let {
            it.lukkSøknadService.lagBrevutkast(trekkSøknadRequest) shouldBe KunneIkkeLageBrevutkast.KunneIkkeLageBrev.left()

            inOrder(
                søknadRepoMock,
                brevServiceMock,
                personServiceMock,
            ) {
                verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
                verify(brevServiceMock).lagBrev(
                    argThat {
                        it shouldBe TrukketSøknadBrevRequest(
                            person,
                            journalførtSøknadMedOppgave,
                            1.januar(2020),
                            "Testbruker, Lokal",
                        )
                    },
                )
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `svarer med ukjentBrevType når det ikke skal lages brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
            personService = personServiceMock,
        ).let {
            it.lukkSøknadService.lagBrevutkast(
                LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                    søknadId = journalførtSøknadMedOppgave.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeLageBrevutkast.UkjentBrevtype.left()

            inOrder(
                søknadRepoMock,
                personServiceMock,
            ) {
                verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
                it.verifyNoMoreInteractions()
            }
        }
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
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
        ).let {
            it.lukkSøknadService.lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.SøknadManglerOppgave.left()

            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknad.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe søknad.id })
            it.verifyNoMoreInteractions()
        }
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
        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
        ).let {
            it.lukkSøknadService.lukkSøknad(
                trekkSøknadRequest.copy(
                    søknadId = nySøknad.id,
                ),
            ) shouldBe KunneIkkeLukkeSøknad.SøknadManglerOppgave.left()

            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe nySøknad.id })
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `Kan ikke lukke oppgave`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn generertPdf.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = MicrosoftGraphApiClientStub,
            clock = fixedEpochClock,
        ).let {
            val actual = it.lukkSøknadService.lukkSøknad(trekkSøknadRequest)
                .getOrHandle { fail { "Skulle gått bra" } }

            actual shouldBe sak

            inOrder(
                søknadRepoMock,
                sakServiceMock,
                brevServiceMock,
                oppgaveServiceMock,
                personServiceMock,
                it.brevService,
            ) {
                verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
                val expectedRequest = TrukketSøknadBrevRequest(
                    person = person,
                    søknad = journalførtSøknadMedOppgave,
                    trukketDato = 1.januar(2020),
                    saksbehandlerNavn = "Testbruker, Lokal",
                )
                verify(brevServiceMock).lagBrev(expectedRequest)
                verify(søknadRepoMock).oppdaterSøknad(
                    argThat {
                        it shouldBe lukketSøknad.copy(
                            oppgaveId = oppgaveId,
                            journalpostId = journalførtSøknadMedOppgaveJournalpostId,
                        )
                    },
                )
                verify(it.brevService).lagreDokument(
                    argThat { dokument ->
                        dokument should beOfType<Dokument.MedMetadata.Informasjon>()
                        dokument.tittel shouldBe expectedRequest.brevInnhold.brevTemplate.tittel()
                        dokument.generertDokument shouldBe generertPdf
                        dokument.generertDokumentJson shouldBe expectedRequest.brevInnhold.toJson()
                        dokument.metadata shouldBe Dokument.Metadata(
                            sakId = sakId,
                            søknadId = journalførtSøknadMedOppgave.id,
                            vedtakId = null,
                            bestillBrev = true,
                        )
                    },
                )
                verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
                verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `Feil ved generering av brev ved lukking`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        ServiceOgMocks(
            søknadRepo = søknadRepoMock,
            brevService = brevServiceMock,
            personService = personServiceMock,
            microsoftGraphApiClient = MicrosoftGraphApiClientStub,
            clock = fixedEpochClock,
        ).let {
            it.lukkSøknadService.lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.KunneIkkeGenerereDokument.left()

            inOrder(
                søknadRepoMock,
                brevServiceMock,
                personServiceMock,
            ) {
                verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
                verify(brevServiceMock).lagBrev(
                    TrukketSøknadBrevRequest(
                        person = person,
                        søknad = journalførtSøknadMedOppgave,
                        trukketDato = 1.januar(2020),
                        saksbehandlerNavn = "Testbruker, Lokal",
                    ),
                )
                it.verifyNoMoreInteractions()
            }
        }
    }

    private class ServiceOgMocks(
        val søknadRepo: SøknadRepo = mock(),
        val sakService: SakService = mock(),
        val brevService: BrevService = mock(),
        val oppgaveService: OppgaveService = mock(),
        val personService: PersonService = mock(),
        val microsoftGraphApiClient: MicrosoftGraphApiOppslag = MicrosoftGraphApiClientStub,
        val clock: Clock = fixedEpochClock,
    ) {
        val lukkSøknadService = LukkSøknadServiceImpl(
            søknadRepo = søknadRepo,
            sakService = sakService,
            brevService = brevService,
            oppgaveService = oppgaveService,
            personService = personService,
            microsoftGraphApiClient = microsoftGraphApiClient,
            clock = clock,
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(
                søknadRepo,
                sakService,
                brevService,
                oppgaveService,
                personService,
            )
        }
    }
}
