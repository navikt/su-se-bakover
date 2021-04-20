package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.ValgtStønadsperiode
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakType
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
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
    private val stønadsperiode = ValgtStønadsperiode(Periode.create(1.januar(2021), 31.desember(2021)))
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
            periodeList = listOf(),
        ),
        utbetalingsrequest = Utbetalingsrequest(value = ""),
    )

    @Test
    fun `svarer med feil dersom vi ikke finner behandling`() {
        val behandling = avslagTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).iverksett(SøknadsbehandlingService.IverksettRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe SøknadsbehandlingService.KunneIkkeIverksette.FantIkkeBehandling.left()

        inOrder(søknadsbehandlingRepoMock, ferdigstillVedtakServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            ferdigstillVedtakServiceMock,
        )
    }

    @Test
    fun `svarer med feil dersom vi ikke kunne simulere`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService>()

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn KunneIkkeUtbetale.KunneIkkeSimulere.left()
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).iverksett(SøknadsbehandlingService.IverksettRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe SøknadsbehandlingService.KunneIkkeIverksette.KunneIkkeKontrollsimulere.left()

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
            ferdigstillVedtakServiceMock,
        )
    }

    @Test
    fun `svarer med feil dersom kontrollsimulering var for ulik`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService>()

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
            utbetalingService = utbetalingServiceMock,
        ).iverksett(SøknadsbehandlingService.IverksettRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe SøknadsbehandlingService.KunneIkkeIverksette.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()

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
            ferdigstillVedtakServiceMock,
        )
    }

    @Test
    fun `svarer med feil dersom vi ikke kunne utbetale`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService>()

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn KunneIkkeUtbetale.Protokollfeil.left()
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).iverksett(SøknadsbehandlingService.IverksettRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe SøknadsbehandlingService.KunneIkkeIverksette.KunneIkkeUtbetale.left()

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
            ferdigstillVedtakServiceMock,
        )
    }

    @Test
    fun `attesterer og iverksetter innvilgning hvis alt er ok`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService>()
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn utbetaling.right()
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val vedtakRepoMock = mock<VedtakRepo>()
        val statistikkObserver = mock<EventObserver>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            vedtakRepo = vedtakRepoMock,
            observer = statistikkObserver,
        ).iverksett(SøknadsbehandlingService.IverksettRequest(behandling.id, Attestering.Iverksatt(attestant)))

        val expected = Søknadsbehandling.Iverksatt.Innvilget(
            id = behandling.id,
            opprettet = behandling.opprettet,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer,
            søknad = behandling.søknad,
            oppgaveId = behandling.oppgaveId,
            behandlingsinformasjon = behandling.behandlingsinformasjon,
            fnr = behandling.fnr,
            beregning = behandling.beregning,
            simulering = behandling.simulering,
            saksbehandler = behandling.saksbehandler,
            attestering = Attestering.Iverksatt(attestant),
            fritekstTilBrev = "",
            stønadsperiode = behandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        response shouldBe expected.right()

        inOrder(
            søknadsbehandlingRepoMock,
            behandlingMetricsMock,
            utbetalingServiceMock,
            vedtakRepoMock,
            statistikkObserver,
        ) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering },
            )
            verify(søknadsbehandlingRepoMock).lagre(expected)
            verify(vedtakRepoMock).lagre(
                argThat {
                    it.behandling shouldBe expected
                    it should beOfType<Vedtak.EndringIYtelse>()
                    it.vedtakType shouldBe VedtakType.SØKNAD
                },
            )
            verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)
            verify(statistikkObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(expected)
                },
            )
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            ferdigstillVedtakServiceMock,
            utbetalingServiceMock,
        )
    }

    @Test
    fun `attesterer og iverksetter avslag hvis alt er ok`() {
        val behandling = avslagTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val ferdigstillVedtakService = mock<FerdigstillVedtakService>() { mock ->
            doAnswer { it ->
                (it.arguments[0] as Vedtak.Avslag.AvslagBeregning).copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(iverksattJournalpostId)).right()
            }.whenever(mock).journalførOgLagre(any())
            doAnswer {
                (it.arguments[0] as Vedtak.Avslag.AvslagBeregning).copy(journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(iverksattJournalpostId, iverksattBrevbestillingId)).right()
            }.whenever(mock).distribuerOgLagre(any())
            doAnswer {
                (it.arguments[0] as Vedtak.Avslag.AvslagBeregning).right()
            }.whenever(mock).lukkOppgaveMedBruker(any())
        }

        val vedtakRepoMock = mock<VedtakRepo>()
        val opprettVedtakssnapshotService = mock<OpprettVedtakssnapshotService>()

        val expectedAvslag = Søknadsbehandling.Iverksatt.Avslag.MedBeregning(
            id = behandling.id,
            opprettet = behandling.opprettet,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer,
            søknad = behandling.søknad,
            oppgaveId = behandling.oppgaveId,
            behandlingsinformasjon = behandling.behandlingsinformasjon,
            fnr = behandling.fnr,
            beregning = behandling.beregning,
            saksbehandler = behandling.saksbehandler,
            attestering = Attestering.Iverksatt(attestant),
            fritekstTilBrev = "",
            stønadsperiode = behandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val statistikkObserver = mock<EventObserver>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            behandlingMetrics = behandlingMetricsMock,
            observer = statistikkObserver,
            vedtakRepo = vedtakRepoMock,
            ferdigstillVedtakService = ferdigstillVedtakService,
            opprettVedtakssnapshotService = opprettVedtakssnapshotService,
        ).iverksett(SøknadsbehandlingService.IverksettRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe expectedAvslag.right()

        inOrder(
            søknadsbehandlingRepoMock,
            behandlingMetricsMock,
            statistikkObserver,
            vedtakRepoMock,
            ferdigstillVedtakService,
            opprettVedtakssnapshotService,
        ) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            verify(ferdigstillVedtakService).journalførOgLagre(
                argThat {
                    it should beOfType<Vedtak.Avslag.AvslagBeregning>()
                    it.vedtakType shouldBe VedtakType.AVSLAG
                },
            )
            verify(søknadsbehandlingRepoMock).lagre(expectedAvslag)
            verify(opprettVedtakssnapshotService).opprettVedtak(any())
            verify(behandlingMetricsMock).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)
            verify(ferdigstillVedtakService).distribuerOgLagre(any())
            verify(ferdigstillVedtakService).lukkOppgaveMedBruker(any())
            verify(statistikkObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(expectedAvslag)
                },
            )
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
        )
    }

    @Test
    fun `iverksett behandling attesterer og saksbehandler kan ikke være samme person`() {
        val behandling = avslagTilAttestering().copy(
            saksbehandler = NavIdentBruker.Saksbehandler(attestant.navIdent),
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).iverksett(SøknadsbehandlingService.IverksettRequest(behandling.id, Attestering.Iverksatt(attestant)))

        response shouldBe SøknadsbehandlingService.KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        inOrder(søknadsbehandlingRepoMock, ferdigstillVedtakServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
        }
        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            ferdigstillVedtakServiceMock,
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
                oppgaveId = søknadOppgaveId,
                fritekstTilBrev = "",
                stønadsperiode = it.stønadsperiode,
                grunnlagsdata = Grunnlagsdata.EMPTY,
            )
        }

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService>()

        assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
            createSøknadsbehandlingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            ).iverksett(SøknadsbehandlingService.IverksettRequest(behandling.id, Attestering.Iverksatt(attestant)))

            inOrder(søknadsbehandlingRepoMock, ferdigstillVedtakServiceMock) {
                verify(søknadsbehandlingRepoMock).hent(behandling.id)
            }
            verifyNoMoreInteractions(
                søknadsbehandlingRepoMock,
                ferdigstillVedtakServiceMock,
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
                journalpostId = BehandlingTestUtils.søknadJournalpostId,
            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            saksbehandler = saksbehandler,
            oppgaveId = søknadOppgaveId,
            beregning = beregning,
            simulering = simulering,
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.EMPTY,
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
                journalpostId = BehandlingTestUtils.søknadJournalpostId,
            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            saksbehandler = saksbehandler,
            oppgaveId = søknadOppgaveId,
            beregning = beregning,
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

    private val beregning = TestBeregning

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(fixedClock),
        nettoBeløp = 191500,
        periodeList = listOf(),
    )

    @Nested
    inner class IverksettStatusovergangFeilMapperTest {
        @Test
        fun `mapper feil fra statusovergang til fornuftige typer for servicelaget`() {
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeJournalføre) shouldBe SøknadsbehandlingService.KunneIkkeIverksette.KunneIkkeJournalføreBrev
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.KunneIkkeKontrollsimulere) shouldBe SøknadsbehandlingService.KunneIkkeIverksette.KunneIkkeKontrollsimulere
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte) shouldBe SøknadsbehandlingService.KunneIkkeIverksette.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.TekniskFeil) shouldBe SøknadsbehandlingService.KunneIkkeIverksette.KunneIkkeUtbetale
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.SaksbehandlerOgAttestantKanIkkeVæreSammePerson) shouldBe SøknadsbehandlingService.KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            SøknadsbehandlingServiceImpl.IverksettStatusovergangFeilMapper.map(Statusovergang.KunneIkkeIverksetteSøknadsbehandling.FantIkkePerson) shouldBe SøknadsbehandlingService.KunneIkkeIverksette.FantIkkePerson
        }
    }
}
