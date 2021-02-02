package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.KunneIkkeUnderkjenneBehandling
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import java.util.UUID

class SøknadsbehandlingServiceUnderkjennTest {
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(0)
    private val søknadId = UUID.randomUUID()
    private val fnr = FnrGenerator.random()
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    private val nyOppgaveId = OppgaveId("999")
    private val aktørId = AktørId("12345")
    private val saksbehandler = NavIdentBruker.Saksbehandler("s")

    private val underkjentAttestering = Attestering.Underkjent(
        attestant = NavIdentBruker.Attestant("a"),
        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
        kommentar = "begrunnelse"
    )

    private val innvilgetBehandlingTilAttestering = Søknadsbehandling.TilAttestering.Innvilget(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        søknad = Søknad.Journalført.MedOppgave(
            id = søknadId,
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = journalpostId
        ),
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "NAVN",
            datoBeregnet = idag(),
            nettoBeløp = 191500,
            periodeList = listOf()
        ),
        saksbehandler = saksbehandler,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        oppgaveId = oppgaveId
    )

    private val oppgaveConfig = OppgaveConfig.Saksbehandling(
        journalpostId = journalpostId,
        søknadId = søknadId,
        aktørId = aktørId,
        tilordnetRessurs = saksbehandler
    )

    private fun createService(
        behandlingRepo: SaksbehandlingRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveService: OppgaveService = mock(),
        søknadService: SøknadService = mock(),
        søknadRepo: SøknadRepo = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        iverksettBehandlingService: IverksettSøknadsbehandlingService = mock(),
        observer: EventObserver = mock { on { handle(any()) }.doNothing() },
        beregningService: BeregningService = mock(),
    ) = SøknadsbehandlingServiceImpl(
        søknadService,
        søknadRepo,
        behandlingRepo,
        utbetalingService,
        personService,
        oppgaveService,
        iverksettBehandlingService,
        behandlingMetrics,
        beregningService,
    ).apply { addObserver(observer) }

    @Test
    fun `Fant ikke behandling`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).underkjenn(
            UnderkjennSøknadsbehandlingRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering
            )
        )

        actual shouldBe KunneIkkeUnderkjenneBehandling.FantIkkeBehandling.left()

        inOrder(behandlingRepoMock) {
            verify(behandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }

    @Test
    fun `Feil behandlingsstatus`() {
        val behandling: Søknadsbehandling.Iverksatt.Innvilget = innvilgetBehandlingTilAttestering.tilIverksatt(
            Attestering.Iverksatt(
                NavIdentBruker.Attestant("attestant")
            ),
            UUID30.randomUUID()
        )

        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        shouldThrow<StatusovergangVisitor.UgyldigStatusovergangException> {
            createService(
                behandlingRepo = behandlingRepoMock,
                oppgaveService = oppgaveServiceMock,
                personService = personServiceMock,
                behandlingMetrics = behandlingMetricsMock
            ).underkjenn(
                UnderkjennSøknadsbehandlingRequest(
                    behandlingId = behandling.id,
                    attestering = underkjentAttestering
                )
            )
        }.msg shouldContain "Ugyldig statusovergang"

        inOrder(behandlingRepoMock) {
            verify(behandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }

    @Test
    fun `attestant kan ikke være den samme som saksbehandler`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }

        val attestantSomErLikSaksbehandler = NavIdentBruker.Attestant(saksbehandler.navIdent)

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val observerMock: EventObserver = mock()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            observer = observerMock
        ).underkjenn(
            UnderkjennSøknadsbehandlingRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = Attestering.Underkjent(
                    attestant = attestantSomErLikSaksbehandler,
                    grunn = underkjentAttestering.grunn,
                    kommentar = underkjentAttestering.kommentar
                )
            )
        )

        actual shouldBe KunneIkkeUnderkjenneBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        inOrder(behandlingRepoMock) {
            verify(behandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
        verifyZeroInteractions(observerMock)
    }

    @Test
    fun `Feiler å underkjenne dersom vi ikke fikk aktør id`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).underkjenn(
            UnderkjennSøknadsbehandlingRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering
            )
        )

        actual shouldBe KunneIkkeUnderkjenneBehandling.FantIkkeAktørId.left()

        inOrder(behandlingRepoMock, personServiceMock) {
            verify(behandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `Klarer ikke opprette oppgave`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).underkjenn(
            UnderkjennSøknadsbehandlingRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering
            )
        )

        actual shouldBe KunneIkkeUnderkjenneBehandling.KunneIkkeOppretteOppgave.left()

        inOrder(behandlingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(argThat { it shouldBe oppgaveConfig })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `Underkjenner selvom vi ikke klarer lukke oppgave`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val observerMock: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            observer = observerMock
        ).underkjenn(
            UnderkjennSøknadsbehandlingRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering
            )
        )

        val underkjentMedNyOppgaveIdOgAttestering = Søknadsbehandling.Underkjent.Innvilget(
            id = innvilgetBehandlingTilAttestering.id,
            opprettet = innvilgetBehandlingTilAttestering.opprettet,
            sakId = innvilgetBehandlingTilAttestering.sakId,
            saksnummer = innvilgetBehandlingTilAttestering.saksnummer,
            søknad = innvilgetBehandlingTilAttestering.søknad,
            oppgaveId = nyOppgaveId,
            behandlingsinformasjon = innvilgetBehandlingTilAttestering.behandlingsinformasjon,
            fnr = innvilgetBehandlingTilAttestering.fnr,
            beregning = innvilgetBehandlingTilAttestering.beregning,
            simulering = innvilgetBehandlingTilAttestering.simulering,
            saksbehandler = innvilgetBehandlingTilAttestering.saksbehandler,
            attestering = underkjentAttestering
        )

        actual shouldBe underkjentMedNyOppgaveIdOgAttestering.right()

        inOrder(
            behandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            observerMock
        ) {
            verify(behandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe oppgaveConfig
                }
            )
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.OPPRETTET_OPPGAVE)

            verify(behandlingRepoMock).lagre(argThat { it shouldBe underkjentMedNyOppgaveIdOgAttestering })

            verify(behandlingMetricsMock).incrementUnderkjentCounter(argThat { it shouldBe BehandlingMetrics.UnderkjentHandlinger.PERSISTERT })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
            verify(observerMock).handle(
                argThat {
                    it shouldBe Event.Statistikk.SøknadsbehandlingUnderkjent(underkjentMedNyOppgaveIdOgAttestering)
                }
            )
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `underkjenner behandling`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).underkjenn(
            UnderkjennSøknadsbehandlingRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering
            )
        )

        val underkjentMedNyOppgaveIdOgAttestering = Søknadsbehandling.Underkjent.Innvilget(
            id = innvilgetBehandlingTilAttestering.id,
            opprettet = innvilgetBehandlingTilAttestering.opprettet,
            sakId = innvilgetBehandlingTilAttestering.sakId,
            saksnummer = innvilgetBehandlingTilAttestering.saksnummer,
            søknad = innvilgetBehandlingTilAttestering.søknad,
            oppgaveId = nyOppgaveId,
            behandlingsinformasjon = innvilgetBehandlingTilAttestering.behandlingsinformasjon,
            fnr = innvilgetBehandlingTilAttestering.fnr,
            beregning = innvilgetBehandlingTilAttestering.beregning,
            simulering = innvilgetBehandlingTilAttestering.simulering,
            saksbehandler = innvilgetBehandlingTilAttestering.saksbehandler,
            attestering = underkjentAttestering
        )

        actual shouldBe underkjentMedNyOppgaveIdOgAttestering.right()

        inOrder(
            behandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        ) {
            verify(behandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe oppgaveConfig
                }
            )
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.OPPRETTET_OPPGAVE)
            verify(behandlingRepoMock).lagre(underkjentMedNyOppgaveIdOgAttestering)
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.PERSISTERT)
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.LUKKET_OPPGAVE)
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }
}
