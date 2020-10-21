package no.nav.su.se.bakover.service.behandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.PdlFeil
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.BeregningRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class BehandlingServiceImplTest {

    private val sakId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")
    private val saksbehandler = Saksbehandler("AB12345")

    @Test
    fun `simuler behandling`() {
        val behandling = beregnetBehandling()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any()) } doReturn simulertUtbetaling.right()
        }
        behandling.simulering() shouldBe null
        behandling.status() shouldBe Behandling.BehandlingsStatus.BEREGNET_INNVILGET

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(behandling.id, saksbehandler)

        response shouldBe behandling.right()
        verify(behandlingRepoMock, Times(2)).hentBehandling(behandling.id)
        verify(utbetalingServiceMock).simulerUtbetaling(
            sakId = argThat { it shouldBe behandling.sakId },
            saksbehandler = argThat { it shouldBe saksbehandler },
            beregning = argThat { it shouldBe beregning }
        )
        verify(behandlingRepoMock).leggTilSimulering(behandling.id, simulering)
        verify(behandlingRepoMock).oppdaterBehandlingStatus(behandling.id, Behandling.BehandlingsStatus.SIMULERT)
    }

    @Test
    fun `simuler behandling gir feilmelding hvis simulering ikke går bra`() {
        val behandling = beregnetBehandling()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(behandling.id, saksbehandler)

        response shouldBe SimuleringFeilet.TEKNISK_FEIL.left()
        verify(behandlingRepoMock).hentBehandling(
            argThat { it shouldBe behandling.id }
        )
        verify(utbetalingServiceMock).simulerUtbetaling(
            sakId = argThat { it shouldBe behandling.sakId },
            saksbehandler = argThat { it shouldBe saksbehandler },
            beregning = argThat { it shouldBe beregning }
        )
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `iverksett behandling betaler ikke ut penger ved avslag`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val utbetalingSericeMock = mock<UtbetalingService>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingSericeMock,
        ).iverksett(behandling.id, attestant)

        response shouldBe behandling.right()
        verify(behandlingRepoMock).hentBehandling(behandling.id)
        verify(behandlingRepoMock).attester(behandling.id, attestant)
        verify(behandlingRepoMock).oppdaterBehandlingStatus(
            behandling.id,
            Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
        )
        verifyZeroInteractions(utbetalingSericeMock)
    }

    @Test
    fun `iverksett behandling betaler ut penger ved innvilgelse`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    any(), any(), any(), any()
                )
            } doReturn oversendtUtbetaling.right()
        }

        val personOppslagMock: PersonOppslag = mock {
            on { aktørId(any()) } doReturn AktørId("12345").right()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            personOppslag = personOppslagMock
        ).iverksett(behandling.id, attestant)

        response shouldBe behandling.right()

        inOrder(
            behandlingRepoMock, utbetalingServiceMock, personOppslagMock
        ) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe behandling.sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering }
            )
            verify(behandlingRepoMock).leggTilUtbetaling(behandling.id, utbetalingForSimulering.id)
            verify(behandlingRepoMock).attester(behandling.id, attestant)
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandling.id,
                Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
            )
            verify(personOppslagMock).aktørId(argThat { it shouldBe fnr })
        }
        verifyNoMoreInteractions(
            behandlingRepoMock, utbetalingServiceMock, personOppslagMock
        )
    }

    @Test
    fun `iverksett behandling gir feilmelding ved inkonsistens i simuleringsresultat`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    behandling.sakId,
                    attestant,
                    beregning,
                    simulering
                )
            } doReturn KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock
        ).iverksett(behandling.id, attestant)

        response shouldBe Behandling.IverksettFeil.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()

        verify(behandlingRepoMock).hentBehandling(behandling.id)
        verify(utbetalingServiceMock).utbetal(behandling.sakId, attestant, beregning, simulering)
        verify(behandlingRepoMock, Times(0)).oppdaterBehandlingStatus(any(), any())
        verifyNoMoreInteractions(utbetalingServiceMock)
    }

    @Test
    fun `iverksett behandling gir feilmelding dersom vi ikke får sendt utbetaling til oppdrag`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    sakId = argThat { it shouldBe behandling.sakId },
                    attestant = argThat { it shouldBe attestant },
                    beregning = argThat { it shouldBe beregning },
                    simulering = argThat { it shouldBe simulering }
                )
            } doReturn KunneIkkeUtbetale.Protokollfeil.left()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock
        ).iverksett(behandling.id, attestant)

        response shouldBe Behandling.IverksettFeil.KunneIkkeUtbetale.left()
    }

    @Test
    fun `lag brev`() {
        val pdfDocument = "some-pdf-document".toByteArray()
        val personMock = mock<Person>() {
            on { navn } doReturn Person.Navn("fornavn", null, "etternavn")
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(fnr) } doReturn personMock.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn pdfDocument.right()
        }

        val response = createService(
            sakService = sakServiceMock,
            personOppslag = personOppslagMock,
            brevService = brevServiceMock
        ).lagBrev(beregnetBehandling())

        response shouldBe pdfDocument.right()
    }

    @Test
    fun `lag brev hente sak feiler`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn FantIkkeSak.left()
        }
        val response = createService(
            sakService = sakServiceMock,
        ).lagBrev(beregnetBehandling())

        response shouldBe KunneIkkeLageBrev.FantIkkeSak.left()
    }

    @Test
    fun `lag brev hente person feiler`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(fnr) } doReturn PdlFeil.FantIkkePerson.left()
        }
        val response = createService(
            sakService = sakServiceMock,
            personOppslag = personOppslagMock
        ).lagBrev(beregnetBehandling())

        response shouldBe KunneIkkeLageBrev.FantIkkePerson.left()
    }

    @Test
    fun `lag brev generering av pdf feiler`() {
        val personMock = mock<Person>() {
            on { navn } doReturn Person.Navn("fornavn", null, "etternavn")
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(fnr) } doReturn personMock.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev.KunneIkkeGenererePdf.left()
        }

        val response = createService(
            sakService = sakServiceMock,
            personOppslag = personOppslagMock,
            brevService = brevServiceMock
        ).lagBrev(beregnetBehandling())

        response shouldBe KunneIkkeLageBrev.KunneIkkeGenererePdf.left()
    }

    private fun beregnetBehandling() = Behandling(
        sakId = sakId,
        søknad = Søknad(sakId = sakId, søknadInnhold = SøknadInnholdTestdataBuilder.build()),
        status = Behandling.BehandlingsStatus.BEREGNET_INNVILGET,
        beregning = beregning,
        fnr = fnr,
        behandlingsinformasjon = Behandlingsinformasjon(
            bosituasjon = Behandlingsinformasjon.Bosituasjon(
                delerBolig = false,
                delerBoligMed = null,
                ektemakeEllerSamboerUnder67År = null,
                ektemakeEllerSamboerUførFlyktning = null,
                begrunnelse = null
            )
        )
    )

    private fun behandlingTilAttestering(status: Behandling.BehandlingsStatus) = beregnetBehandling().copy(
        simulering = simulering,
        status = status
    )

    private val attestant = Attestant("SU")

    private val oppdragsmelding = Utbetalingsrequest(
        value = ""
    )

    private val beregning = Beregning(
        fraOgMed = 1.januar(2020),
        tilOgMed = 31.januar(2020),
        sats = Sats.HØY,
        fradrag = listOf()
    )

    private val oppdrag = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = sakId,
    )

    private val sak = Sak(
        id = sakId,
        fnr = fnr,
        oppdrag = oppdrag
    )

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(),
        nettoBeløp = 191500,
        periodeList = listOf()
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        utbetalingslinjer = listOf(),
        fnr = fnr,
        type = Utbetaling.UtbetalingsType.NY,
        oppdragId = oppdrag.id,
        behandler = attestant,
        avstemmingsnøkkel = Avstemmingsnøkkel()
    )

    private val simulertUtbetaling = utbetalingForSimulering.toSimulertUtbetaling(simulering)
    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)

    private fun createService(
        behandlingRepo: BehandlingRepo = mock(),
        hendelsesloggRepo: HendelsesloggRepo = mock(),
        beregningRepo: BeregningRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveClient: OppgaveClient = mock(),
        søknadService: SøknadService = mock(),
        sakService: SakService = mock(),
        personOppslag: PersonOppslag = mock(),
        brevService: BrevService = mock()
    ) = BehandlingServiceImpl(
        behandlingRepo = behandlingRepo,
        hendelsesloggRepo = hendelsesloggRepo,
        beregningRepo = beregningRepo,
        utbetalingService = utbetalingService,
        oppgaveClient = oppgaveClient,
        søknadService = søknadService,
        sakService = sakService,
        personOppslag = personOppslag,
        brevService = brevService
    )
}
