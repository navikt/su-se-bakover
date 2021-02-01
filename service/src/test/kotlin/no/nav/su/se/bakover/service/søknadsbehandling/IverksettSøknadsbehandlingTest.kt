package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Statusovergang
import no.nav.su.se.bakover.domain.behandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.IverksettSøknadsbehandlingRequest
import no.nav.su.se.bakover.service.SaksbehandlingServiceImpl
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.behandling.IverksettSaksbehandlingService
import no.nav.su.se.bakover.service.behandling.KunneIkkeIverksetteBehandling
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class IverksettSøknadsbehandlingTest {
    private val fnr = FnrGenerator.random()
    private val sakId: UUID = UUID.fromString("268e62fb-3079-4e8d-ab32-ff9fb9eac2ec")
    private val behandlingId: UUID = UUID.fromString("a602aa68-c989-43e3-9fb7-cb488a2a3821")
    private val saksnummer = Saksnummer(999999)
    private val oppgaveId = OppgaveId("o")
    private val iverksattJournalpostId = JournalpostId("j")
    private val iverksattBrevbestillingId = BrevbestillingId("2")
    private val søknadOppgaveId = OppgaveId("søknadOppgaveId")
    private val attestant = NavIdentBruker.Attestant("attestant")
    private val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlinger")
    private val utbetalingId = UUID30.randomUUID()

    @Test
    fun `svarer med feil dersom vi ikke finner behandling`() {
        val behandling = avslagTilAttestering()

        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val iverksettSaksbehandlingServiceMock = mock<IverksettSaksbehandlingService>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            iverksettBehandlingService = iverksettSaksbehandlingServiceMock
        ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe KunneIkkeIverksetteBehandling.FantIkkeBehandling.left()

        inOrder(behandlingRepoMock, iverksettSaksbehandlingServiceMock) {
            verify(behandlingRepoMock).hent(behandling.id)
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            iverksettSaksbehandlingServiceMock
        )
    }

    @Test
    fun `attesterer og iverksetter innvilgning hvis alt er ok`() {
        val behandling = innvilgetTilAttestering()

        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val iverksettSaksbehandlingServiceMock = mock<IverksettSaksbehandlingService>() {
            on { iverksettInnvilgning(any(), any()) } doReturn utbetalingId.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            iverksettBehandlingService = iverksettSaksbehandlingServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

        val expected = Søknadsbehandling.Iverksatt.Innvilget(
            id = behandling.id,
            opprettet = behandling.opprettet,
            søknad = behandling.søknad,
            behandlingsinformasjon = behandling.behandlingsinformasjon,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer,
            fnr = behandling.fnr,
            saksbehandler = behandling.saksbehandler,
            oppgaveId = behandling.oppgaveId,
            beregning = behandling.beregning,
            simulering = behandling.simulering,
            attestering = Attestering.Iverksatt(attestant),
            utbetalingId = utbetalingId
        )

        response shouldBe expected.right()

        inOrder(behandlingRepoMock, iverksettSaksbehandlingServiceMock, behandlingMetricsMock) {
            verify(behandlingRepoMock).hent(behandling.id)
            verify(iverksettSaksbehandlingServiceMock).iverksettInnvilgning(behandling, attestant)
            verify(behandlingRepoMock).lagre(expected)
            verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            iverksettSaksbehandlingServiceMock
        )
    }

    @Test
    fun `attesterer og iverksetter avslag hvis alt er ok`() {
        val behandling = avslagTilAttestering()

        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val expectedJournalført = Søknadsbehandling.Iverksatt.Avslag.MedBeregning(
            id = behandling.id,
            opprettet = behandling.opprettet,
            søknad = behandling.søknad,
            behandlingsinformasjon = behandling.behandlingsinformasjon,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer,
            fnr = behandling.fnr,
            saksbehandler = behandling.saksbehandler,
            oppgaveId = behandling.oppgaveId,
            beregning = behandling.beregning,
            attestering = Attestering.Iverksatt(attestant),
            eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.Journalført(
                iverksattJournalpostId
            )
        )

        val expectedJournalførtOgDistribuert = expectedJournalført.copy(
            eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.JournalførtOgDistribuertBrev(
                iverksattJournalpostId, iverksattBrevbestillingId
            )
        )

        val iverksettSaksbehandlingServiceMock = mock<IverksettSaksbehandlingService>() {
            on { opprettJournalpostForAvslag(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrevOgLukkOppgaveForAvslag(any()) } doReturn expectedJournalførtOgDistribuert
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            iverksettBehandlingService = iverksettSaksbehandlingServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe expectedJournalførtOgDistribuert.right()

        inOrder(behandlingRepoMock, iverksettSaksbehandlingServiceMock, behandlingMetricsMock) {
            verify(behandlingRepoMock).hent(behandling.id)
            verify(iverksettSaksbehandlingServiceMock).opprettJournalpostForAvslag(behandling, attestant)
            verify(behandlingRepoMock).lagre(expectedJournalført)
            verify(behandlingMetricsMock).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)
            verify(iverksettSaksbehandlingServiceMock).distribuerBrevOgLukkOppgaveForAvslag(expectedJournalført)
            verify(behandlingRepoMock).lagre(expectedJournalførtOgDistribuert)
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            iverksettSaksbehandlingServiceMock
        )
    }

    @Test
    fun `iverksett behandling attesterer og saksbehandler kan ikke være samme person`() {
        val behandling = avslagTilAttestering().copy(
            saksbehandler = NavIdentBruker.Saksbehandler(attestant.navIdent)
        )

        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val iverksettSaksbehandlingServiceMock = mock<IverksettSaksbehandlingService>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            iverksettBehandlingService = iverksettSaksbehandlingServiceMock
        ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        inOrder(behandlingRepoMock, iverksettSaksbehandlingServiceMock) {
            verify(behandlingRepoMock).hent(behandling.id)
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            iverksettSaksbehandlingServiceMock,
        )
    }

    @Test
    fun `iverksett behandling kaster exception ved ugyldig statusovergang`() {
        val behandling: Søknadsbehandling.Vilkårsvurdert.Innvilget = avslagTilAttestering().let {
            Søknadsbehandling.Vilkårsvurdert.Innvilget(
                id = it.id,
                opprettet = it.opprettet,
                søknad = it.søknad,
                behandlingsinformasjon = it.behandlingsinformasjon,
                sakId = it.sakId,
                saksnummer = it.saksnummer,
                fnr = it.fnr,
                oppgaveId = søknadOppgaveId
            )
        }

        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val iverksettSaksbehandlingServiceMock = mock<IverksettSaksbehandlingService>()

        assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
            createService(
                behandlingRepo = behandlingRepoMock,
                iverksettBehandlingService = iverksettSaksbehandlingServiceMock
            ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

            inOrder(behandlingRepoMock, iverksettSaksbehandlingServiceMock) {
                verify(behandlingRepoMock).hent(behandling.id)
            }
            verifyNoMoreInteractions(
                behandlingRepoMock,
                iverksettSaksbehandlingServiceMock,
            )
        }
    }

    private fun innvilgetTilAttestering() =
        Søknadsbehandling.TilAttestering.Innvilget(
            id = behandlingId,
            opprettet = Tidspunkt.now(),
            søknad = Søknad.Journalført.MedOppgave(
                id = BehandlingTestUtils.søknadId,
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = BehandlingTestUtils.søknadOppgaveId,
                journalpostId = BehandlingTestUtils.søknadJournalpostId
            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            saksbehandler = saksbehandler,
            oppgaveId = søknadOppgaveId,
            beregning = beregning,
            simulering = simulering,
        )

    private fun avslagTilAttestering() =
        Søknadsbehandling.TilAttestering.Avslag.MedBeregning(
            id = behandlingId,
            opprettet = Tidspunkt.now(),
            søknad = Søknad.Journalført.MedOppgave(
                id = BehandlingTestUtils.søknadId,
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = BehandlingTestUtils.søknadOppgaveId,
                journalpostId = BehandlingTestUtils.søknadJournalpostId
            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            saksbehandler = saksbehandler,
            oppgaveId = søknadOppgaveId,
            beregning = beregning,
        )

    private val beregning = TestBeregning

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(fixedClock),
        nettoBeløp = 191500,
        periodeList = listOf()
    )

    private fun createService(
        behandlingRepo: SaksbehandlingRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveService: OppgaveService = mock(),
        søknadService: SøknadService = mock(),
        søknadRepo: SøknadRepo = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        iverksettBehandlingService: IverksettSaksbehandlingService = mock(),
        observer: EventObserver = mock { on { handle(any()) }.doNothing() },
        beregningService: BeregningService = mock(),
    ) = SaksbehandlingServiceImpl(
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

    @Nested
    inner class IverksettStatusovergangFeilMapperTest {
        @Test
        fun `mapper feil fra statusovergang til fornuftige typer for servicelaget`() {
            SaksbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeJournalføre) shouldBe KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev
            SaksbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.KunneIkkeKontrollsimulere) shouldBe KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere
            SaksbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte) shouldBe KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
            SaksbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.TekniskFeil) shouldBe KunneIkkeIverksetteBehandling.KunneIkkeUtbetale
            SaksbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.SaksbehandlerOgAttestantKanIkkeVæreSammePerson) shouldBe KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            SaksbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.FantIkkePerson) shouldBe KunneIkkeIverksetteBehandling.FantIkkePerson
        }
    }
}
