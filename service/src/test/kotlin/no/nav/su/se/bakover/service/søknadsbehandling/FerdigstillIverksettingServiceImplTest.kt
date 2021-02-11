package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doReturnConsecutively
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.RevurderingRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.attestant
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.fnr
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.person
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksnummer
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class FerdigstillIverksettingServiceImplTest {

    private val iverksattOppgaveId = OppgaveId("iverksattOppgaveId")

    private val iverksattJournalpostId = JournalpostId("iverksattJournalpostId")

    private val iverksattBrevbestillingId = BrevbestillingId("iverattBrevbestillingId")

    private val utbetalingId = UUID30.randomUUID()

    private val innvilgetBehandlingUtenJournalpost = Søknadsbehandling.Iverksatt.Innvilget(
        id = UUID.randomUUID(),
        sakId = UUID.randomUUID(),
        opprettet = Tidspunkt.EPOCH,
        saksbehandler = saksbehandler,
        attestering = Attestering.Iverksatt(attestant),
        oppgaveId = iverksattOppgaveId,
        saksnummer = saksnummer,
        søknad = Søknad.Journalført.MedOppgave(
            id = BehandlingTestUtils.søknadId,
            opprettet = Tidspunkt.EPOCH,
            sakId = BehandlingTestUtils.sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = BehandlingTestUtils.søknadOppgaveId,
            journalpostId = BehandlingTestUtils.søknadJournalpostId
        ),
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        behandlingsinformasjon = behandlingsinformasjon,
        fnr = fnr,
        utbetalingId = utbetalingId,
        eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering
    )

    private val iverksattRevurdering = IverksattRevurdering(
        id = UUID.randomUUID(),
        periode = Periode.create(1.januar(2021), 31.mars(2021)),
        opprettet = Tidspunkt.EPOCH,
        tilRevurdering = innvilgetBehandlingUtenJournalpost,
        saksbehandler = NavIdentBruker.Saksbehandler("Z123"),
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = innvilgetBehandlingUtenJournalpost.fnr,
            gjelderNavn = "",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        oppgaveId = OppgaveId(""),
        attestant = NavIdentBruker.Attestant(navIdent = "Z321"),
        utbetalingId = UUID30.randomUUID()
    )

    @Test
    fun `kaster exception hvis det ikke finnes noe som kan ferdigstilles for aktuell utbetaling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>() {
            on { hentBehandlingForUtbetaling(any()) } doReturn null
        }

        val revurderingRepoMock = mock<RevurderingRepo>() {
            on { hentRevurderingForUtbetaling(any()) } doReturn null
        }

        assertThrows<IllegalStateException> {
            createFerdigstillIverksettingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
                revurderingRepo = revurderingRepoMock
            ).ferdigstillIverksetting(UUID30.randomUUID())
        }
    }

    @Test
    fun `kaster exception hvis det finnes mange ting som kan ferdigstilles for aktuell utbetaling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>() {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val revurderingRepoMock = mock<RevurderingRepo>() {
            on { hentRevurderingForUtbetaling(any()) } doReturn iverksattRevurdering
        }

        assertThrows<IllegalStateException> {
            createFerdigstillIverksettingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
                revurderingRepo = revurderingRepoMock
            ).ferdigstillIverksetting(UUID30.randomUUID())
        }
    }

    @Test
    fun `Kunne ikke opprette journalpost hvis vi ikke finner saksbehandler`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>() {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) }.doReturn(
                MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left(),
                Either.right(BehandlingTestUtils.microsoftGraphMock.response)
            )
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val statistikkObserver = mock<EventObserver>()

        val actual = createFerdigstillIverksettingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            eventObserver = statistikkObserver
        ).ferdigstillIverksetting(utbetalingId)

        actual shouldBe Unit

        inOrder(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock,
            statistikkObserver
        ) {
            verify(søknadsbehandlingRepoMock).hentBehandlingForUtbetaling(utbetalingId)
            verify(statistikkObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(
                        innvilgetBehandlingUtenJournalpost
                    )
                }
            )
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            verify(oppslagMock).hentBrukerinformasjonForNavIdent(
                argThat {
                    it shouldBe saksbehandler
                }
            )
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `Kunne ikke opprette journalpost hvis vi ikke finner attestant`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>() {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                BehandlingTestUtils.microsoftGraphMock.response.right(),
                MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
            )
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createFerdigstillIverksettingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
        ).ferdigstillIverksetting(utbetalingId)
        actual shouldBe Unit

        inOrder(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock
        ) {
            verify(søknadsbehandlingRepoMock).hentBehandlingForUtbetaling(utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `Kunne ikke opprette journalpost hvis vi ikke finner person`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>() {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn BehandlingTestUtils.microsoftGraphMock.response.right()
        }

        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createFerdigstillIverksettingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            brevService = brevServiceMock

        ).ferdigstillIverksetting(utbetalingId)

        actual shouldBe Unit

        inOrder(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            oppslagMock
        ) {
            verify(søknadsbehandlingRepoMock).hentBehandlingForUtbetaling(utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            brevServiceMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `Kan ikke journalføre eller distribuere brev hvis journalføring feiler`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>() {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = saksbehandler.navIdent).right(),
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = attestant.navIdent).right(),
            )
        }
        val brevServiceMock = mock<BrevService>() {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }
        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val statistikkObserver = mock<EventObserver>()

        val actual = createFerdigstillIverksettingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            eventObserver = statistikkObserver
        ).ferdigstillIverksetting(utbetalingId)

        actual shouldBe Unit

        inOrder(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            brevServiceMock,
            oppgaveServiceMock,
            statistikkObserver
        ) {
            verify(søknadsbehandlingRepoMock).hentBehandlingForUtbetaling(utbetalingId)
            verify(statistikkObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(
                        innvilgetBehandlingUtenJournalpost
                    )
                }
            )
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = innvilgetBehandlingUtenJournalpost.beregning,
                        behandlingsinformasjon = innvilgetBehandlingUtenJournalpost.behandlingsinformasjon,
                        saksbehandlerNavn = saksbehandler.navIdent,
                        attestantNavn = attestant.navIdent,
                    )
                },
                argThat { it shouldBe saksnummer }
            )
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `Kunne ikke distribuere brev`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>() {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = saksbehandler.navIdent).right(),
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = attestant.navIdent).right(),
            )
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createFerdigstillIverksettingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            brevService = brevServiceMock,
        ).ferdigstillIverksetting(utbetalingId)

        actual shouldBe Unit

        inOrder(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            brevServiceMock,
            oppgaveServiceMock
        ) {
            verify(søknadsbehandlingRepoMock).hentBehandlingForUtbetaling(utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = innvilgetBehandlingUtenJournalpost.beregning,
                        behandlingsinformasjon = innvilgetBehandlingUtenJournalpost.behandlingsinformasjon,
                        saksbehandlerNavn = saksbehandler.navIdent,
                        attestantNavn = attestant.navIdent,
                    )
                },
                argThat { it shouldBe saksnummer },
            )
            verify(søknadsbehandlingRepoMock).lagre(
                argThat {
                    it shouldBe innvilgetBehandlingUtenJournalpost.copy(
                        eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.Journalført(
                            iverksattJournalpostId
                        )
                    )
                }
            )
            verify(brevServiceMock).distribuerBrev(
                argThat {
                    it shouldBe iverksattJournalpostId
                }
            )
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            brevServiceMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `journalfører og distribuerer brev for iverksatt innvilget`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>() {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrev(any()) } doReturn iverksattBrevbestillingId.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = saksbehandler.navIdent).right(),
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = attestant.navIdent).right(),
            )
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val statistikkObserver = mock<EventObserver>()

        val actual = createFerdigstillIverksettingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            brevService = brevServiceMock,
            eventObserver = statistikkObserver
        ).ferdigstillIverksetting(utbetalingId)

        actual shouldBe Unit

        verify(søknadsbehandlingRepoMock).hentBehandlingForUtbetaling(utbetalingId)
        verify(statistikkObserver).handle(
            argThat {
                it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(
                    innvilgetBehandlingUtenJournalpost
                )
            }
        )
        verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
        argumentCaptor<NavIdentBruker>().apply {
            verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
            firstValue shouldBe saksbehandler
            secondValue shouldBe attestant
        }
        verify(brevServiceMock).journalførBrev(
            argThat {
                it shouldBe LagBrevRequest.InnvilgetVedtak(
                    person = person,
                    beregning = innvilgetBehandlingUtenJournalpost.beregning,
                    behandlingsinformasjon = innvilgetBehandlingUtenJournalpost.behandlingsinformasjon,
                    saksbehandlerNavn = saksbehandler.navIdent,
                    attestantNavn = attestant.navIdent,
                )
            },
            argThat { it shouldBe saksnummer },
        )
        argumentCaptor<Søknadsbehandling>().apply {
            verify(søknadsbehandlingRepoMock, times(2)).lagre(capture())
            firstValue shouldBe innvilgetBehandlingUtenJournalpost.copy(
                eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.Journalført(
                    iverksattJournalpostId
                )
            )
            secondValue shouldBe innvilgetBehandlingUtenJournalpost.copy(
                eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.JournalførtOgDistribuertBrev(
                    iverksattJournalpostId,
                    iverksattBrevbestillingId
                )
            )
        }
        verify(brevServiceMock).distribuerBrev(
            argThat {
                it shouldBe iverksattJournalpostId
            }
        )
        verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            brevServiceMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock
        )
    }

    private fun createFerdigstillIverksettingService(
        søknadsbehandlingRepo: SøknadsbehandlingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
        brevService: BrevService = mock(),
        eventObserver: EventObserver = mock { on { handle(any()) }.doNothing() },
        clock: Clock = Clock.systemUTC(),
        revurderingRepo: RevurderingRepo = mock()
    ) = FerdigstillIverksettingServiceImpl(
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        oppgaveService = oppgaveService,
        behandlingMetrics = behandlingMetrics,
        microsoftGraphApiClient = microsoftGraphApiOppslag,
        personService = personService,
        brevService = brevService,
        clock = clock,
        revurderingRepo
    ).apply { addObserver(eventObserver) }
}
