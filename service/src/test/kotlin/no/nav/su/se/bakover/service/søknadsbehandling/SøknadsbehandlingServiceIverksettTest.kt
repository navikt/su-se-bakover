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
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.behandling.KunneIkkeIverksetteBehandling
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZoneOffset
import java.util.UUID

internal class SøknadsbehandlingServiceIverksettTest {
    private val fnr = FnrGenerator.random()
    private val sakId: UUID = UUID.fromString("268e62fb-3079-4e8d-ab32-ff9fb9eac2ec")
    private val behandlingId: UUID = UUID.fromString("a602aa68-c989-43e3-9fb7-cb488a2a3821")
    private val saksnummer = Saksnummer(999999)
    private val iverksattJournalpostId = JournalpostId("journalpostId")
    private val iverksattBrevbestillingId = BrevbestillingId("brevbestillingId")
    private val søknadOppgaveId = OppgaveId("søknadOppgaveId")
    private val attestant = NavIdentBruker.Attestant("attestant")
    private val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlinger")
    private val utbetalingId = UUID30.randomUUID()
    val opprettet = Tidspunkt.now(fixedClock)
    private val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
        id = utbetalingId,
        opprettet = opprettet,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = emptyList(),
        type = Utbetaling.UtbetalingsType.NY,
        behandler = attestant,
        avstemmingsnøkkel = Avstemmingsnøkkel(),
        simulering = Simulering(
            gjelderId = FnrGenerator.random(),
            gjelderNavn = "gjelderNavn",
            datoBeregnet = opprettet.toLocalDate(ZoneOffset.UTC),
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        utbetalingsrequest = Utbetalingsrequest(value = ""),
    )

    @Test
    fun `svarer med feil dersom vi ikke finner behandling`() {
        val behandling = avslagTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val iverksettSaksbehandlingServiceMock = mock<IverksettSøknadsbehandlingService>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            iverksettBehandlingService = iverksettSaksbehandlingServiceMock
        ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe KunneIkkeIverksetteBehandling.FantIkkeBehandling.left()

        inOrder(søknadsbehandlingRepoMock, iverksettSaksbehandlingServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            iverksettSaksbehandlingServiceMock
        )
    }

    @Test
    fun `svarer med feil dersom vi ikke kunne simulere`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val iverksettSaksbehandlingServiceMock = mock<IverksettSøknadsbehandlingService>()

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn KunneIkkeUtbetale.KunneIkkeSimulere.left()
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            iverksettBehandlingService = iverksettSaksbehandlingServiceMock,
            utbetalingService = utbetalingServiceMock,
        ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere.left()

        inOrder(søknadsbehandlingRepoMock, utbetalingServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering },
            )
        }
        verifyNoMoreInteractions(
            utbetalingServiceMock,
            søknadsbehandlingRepoMock,
            iverksettSaksbehandlingServiceMock
        )
    }

    @Test
    fun `svarer med feil dersom kontrollsimulering var for ulik`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val iverksettSaksbehandlingServiceMock = mock<IverksettSøknadsbehandlingService>()

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } doReturn KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            iverksettBehandlingService = iverksettSaksbehandlingServiceMock,
            utbetalingService = utbetalingServiceMock,
        ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()

        inOrder(søknadsbehandlingRepoMock, utbetalingServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering },
            )
        }
        verifyNoMoreInteractions(
            utbetalingServiceMock,
            søknadsbehandlingRepoMock,
            iverksettSaksbehandlingServiceMock
        )
    }

    @Test
    fun `svarer med feil dersom vi ikke kunne utbetale`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val iverksettSaksbehandlingServiceMock = mock<IverksettSøknadsbehandlingService>()

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn KunneIkkeUtbetale.Protokollfeil.left()
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            iverksettBehandlingService = iverksettSaksbehandlingServiceMock,
            utbetalingService = utbetalingServiceMock,
        ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe KunneIkkeIverksetteBehandling.KunneIkkeUtbetale.left()

        inOrder(søknadsbehandlingRepoMock, utbetalingServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering },
            )
        }
        verifyNoMoreInteractions(
            utbetalingServiceMock,
            søknadsbehandlingRepoMock,
            iverksettSaksbehandlingServiceMock
        )
    }

    @Test
    fun `attesterer og iverksetter innvilgning hvis alt er ok`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val iverksettSaksbehandlingServiceMock = mock<IverksettSøknadsbehandlingService>()
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn utbetaling.right()
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            iverksettBehandlingService = iverksettSaksbehandlingServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            utbetalingService = utbetalingServiceMock,
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

        inOrder(
            søknadsbehandlingRepoMock,
            behandlingMetricsMock,
            utbetalingServiceMock
        ) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering },
            )
            verify(søknadsbehandlingRepoMock).lagre(expected)
            verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            iverksettSaksbehandlingServiceMock,
            utbetalingServiceMock
        )
    }

    @Test
    fun `attesterer og iverksetter avslag hvis alt er ok`() {
        val behandling = avslagTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
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

        val iverksettSaksbehandlingServiceMock = mock<IverksettSøknadsbehandlingService> {
            on { opprettJournalpostForAvslag(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrevOgLukkOppgaveForAvslag(any()) } doReturn expectedJournalførtOgDistribuert
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            iverksettBehandlingService = iverksettSaksbehandlingServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe expectedJournalførtOgDistribuert.right()

        inOrder(søknadsbehandlingRepoMock, iverksettSaksbehandlingServiceMock, behandlingMetricsMock) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            verify(iverksettSaksbehandlingServiceMock).opprettJournalpostForAvslag(behandling, attestant)
            verify(søknadsbehandlingRepoMock).lagre(expectedJournalført)
            verify(behandlingMetricsMock).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)
            verify(iverksettSaksbehandlingServiceMock).distribuerBrevOgLukkOppgaveForAvslag(expectedJournalført)
            verify(søknadsbehandlingRepoMock).lagre(expectedJournalførtOgDistribuert)
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            iverksettSaksbehandlingServiceMock
        )
    }

    @Test
    fun `iverksett behandling attesterer og saksbehandler kan ikke være samme person`() {
        val behandling = avslagTilAttestering().copy(
            saksbehandler = NavIdentBruker.Saksbehandler(attestant.navIdent)
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val iverksettSaksbehandlingServiceMock = mock<IverksettSøknadsbehandlingService>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            iverksettBehandlingService = iverksettSaksbehandlingServiceMock
        ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        inOrder(søknadsbehandlingRepoMock, iverksettSaksbehandlingServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
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

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val iverksettSaksbehandlingServiceMock = mock<IverksettSøknadsbehandlingService>()

        assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
            createSøknadsbehandlingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
                iverksettBehandlingService = iverksettSaksbehandlingServiceMock
            ).iverksett(IverksettSøknadsbehandlingRequest(behandling.id, Attestering.Iverksatt(attestant)))

            inOrder(søknadsbehandlingRepoMock, iverksettSaksbehandlingServiceMock) {
                verify(søknadsbehandlingRepoMock).hent(behandling.id)
            }
            verifyNoMoreInteractions(
                søknadsbehandlingRepoMock,
                iverksettSaksbehandlingServiceMock,
            )
        }
    }

    private fun innvilgetTilAttestering() =
        Søknadsbehandling.TilAttestering.Innvilget(
            id = behandlingId,
            opprettet = opprettet,
            søknad = Søknad.Journalført.MedOppgave(
                id = BehandlingTestUtils.søknadId,
                opprettet = opprettet,
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
            opprettet = opprettet,
            søknad = Søknad.Journalført.MedOppgave(
                id = BehandlingTestUtils.søknadId,
                opprettet = opprettet,
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

    @Nested
    inner class IverksettStatusovergangFeilMapperTest {
        @Test
        fun `mapper feil fra statusovergang til fornuftige typer for servicelaget`() {
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeJournalføre) shouldBe KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.KunneIkkeKontrollsimulere) shouldBe KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte) shouldBe KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.TekniskFeil) shouldBe KunneIkkeIverksetteBehandling.KunneIkkeUtbetale
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.SaksbehandlerOgAttestantKanIkkeVæreSammePerson) shouldBe KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.FantIkkePerson) shouldBe KunneIkkeIverksetteBehandling.FantIkkePerson
        }
    }
}
