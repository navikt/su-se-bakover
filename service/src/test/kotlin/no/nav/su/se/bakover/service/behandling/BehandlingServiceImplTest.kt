package no.nav.su.se.bakover.service.behandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.createService
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class BehandlingServiceImplTest {

    private val sakId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")
    private val saksbehandler = Saksbehandler("AB12345")
    private val aktørId = AktørId("1234567890123")
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")

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

        val personOppslagMock: PersonOppslag = mock {
            on { aktørId(any()) } doReturn aktørId.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn JournalpostId("1").right()
            on { distribuerBrev(any()) } doReturn "2".right()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val utbetalingSericeMock = mock<UtbetalingService>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingSericeMock,
            brevService = brevServiceMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock
        ).iverksett(behandling.id, attestant)

        response shouldBe behandling.right()
        inOrder(behandlingRepoMock, brevServiceMock, personOppslagMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
            verify(behandlingRepoMock).oppdaterAttestant(behandling.id, attestant)
            verify(behandlingRepoMock).oppdaterBehandlingStatus(behandling.id, Behandling.BehandlingsStatus.IVERKSATT_AVSLAG)
            verify(brevServiceMock).journalførBrev(LagBrevRequest.AvslagsVedtak(behandling), behandling.sakId)
            verify(brevServiceMock).distribuerBrev(JournalpostId("1"))
        }
        verifyNoMoreInteractions(behandlingRepoMock, brevServiceMock, personOppslagMock, oppgaveServiceMock)
    }

    @Test
    fun `returnerer med feilmelding dersom journalføring av brev feiler`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock: PersonOppslag = mock {
            on { aktørId(any()) } doReturn aktørId.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock
        ).iverksett(behandling.id, attestant)

        response shouldBe KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev.left()
        inOrder(behandlingRepoMock, brevServiceMock, personOppslagMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)

            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)

            verify(behandlingRepoMock).oppdaterAttestant(behandling.id, attestant)
            verify(behandlingRepoMock).oppdaterBehandlingStatus(behandling.id, Behandling.BehandlingsStatus.IVERKSATT_AVSLAG)

            verify(brevServiceMock).journalførBrev(LagBrevRequest.AvslagsVedtak(behandling), behandling.sakId)
        }
        verifyNoMoreInteractions(behandlingRepoMock, brevServiceMock, personOppslagMock, oppgaveServiceMock)
    }

    @Test
    fun `returnerer med feilmelding dersom bestilling av distribusjon av brev feiler`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn JournalpostId("1").right()
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn oversendtUtbetaling.right()
        }

        val personOppslagMock: PersonOppslag = mock()

        val oppgaveServiceMock: OppgaveService = mock {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            brevService = brevServiceMock,
        ).iverksett(behandling.id, attestant)

        response shouldBe KunneIkkeIverksetteBehandling.KunneIkkeDistribuereBrev.left()

        inOrder(behandlingRepoMock, brevServiceMock, utbetalingServiceMock, personOppslagMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(behandlingRepoMock).leggTilUtbetaling(behandling.id, utbetalingForSimulering.id)
            verify(behandlingRepoMock).oppdaterAttestant(behandling.id, attestant)
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandling.id,
                Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
            )
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
            verify(brevServiceMock).journalførBrev(LagBrevRequest.InnvilgetVedtak(behandling), behandling.sakId)
            verify(brevServiceMock).distribuerBrev(JournalpostId("1"))
        }
        verifyNoMoreInteractions(behandlingRepoMock, brevServiceMock, personOppslagMock, oppgaveServiceMock)
    }

    @Test
    fun `iverksett behandling betaler ut penger ved innvilgelse`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn JournalpostId("1").right()
            on { distribuerBrev(any()) } doReturn "2".right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    any(), any(), any(), any()
                )
            } doReturn oversendtUtbetaling.right()
        }

        val personOppslagMock: PersonOppslag = mock {
            on { aktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            // TODO jah: Trekk ut dette til en OppgaveService i egen PR og mock OppgaveService istedet. Legg da på verifisering
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            brevService = brevServiceMock
        ).iverksett(behandling.id, attestant)

        response shouldBe behandling.right()

        inOrder(
            behandlingRepoMock, utbetalingServiceMock, personOppslagMock, brevServiceMock
        ) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe behandling.sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering }
            )
            verify(behandlingRepoMock).leggTilUtbetaling(behandling.id, utbetalingForSimulering.id)
            verify(behandlingRepoMock).oppdaterAttestant(behandling.id, attestant)
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandling.id,
                Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
            )
            verify(brevServiceMock).journalførBrev(LagBrevRequest.InnvilgetVedtak(behandling), behandling.sakId)
            verify(brevServiceMock).distribuerBrev(JournalpostId("1"))
        }
        verifyNoMoreInteractions(behandlingRepoMock, utbetalingServiceMock, personOppslagMock)
    }

    @Test
    fun `iverksett behandling gir feilmelding ved inkonsistens i simuleringsresultat`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock: PersonOppslag = mock {
            on { aktørId(any()) } doReturn aktørId.right()
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
            utbetalingService = utbetalingServiceMock,
            personOppslag = personOppslagMock
        ).iverksett(behandling.id, attestant)

        response shouldBe KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()

        inOrder(behandlingRepoMock, personOppslagMock, utbetalingServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(utbetalingServiceMock).utbetal(behandling.sakId, attestant, beregning, simulering)
            verify(behandlingRepoMock, Times(0)).oppdaterBehandlingStatus(any(), any())
        }
        verifyNoMoreInteractions(behandlingRepoMock, personOppslagMock, utbetalingServiceMock)
    }

    @Test
    fun `iverksett behandling gir feilmelding dersom vi ikke får sendt utbetaling til oppdrag`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock: PersonOppslag = mock {
            on { aktørId(any()) } doReturn aktørId.right()
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
            utbetalingService = utbetalingServiceMock,
            personOppslag = personOppslagMock
        ).iverksett(behandling.id, attestant)

        response shouldBe KunneIkkeIverksetteBehandling.KunneIkkeUtbetale.left()

        inOrder(behandlingRepoMock, personOppslagMock, utbetalingServiceMock) {
            verify(behandlingRepoMock).hentBehandling(
                argThat {
                    it shouldBe behandling.id
                }
            )
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe behandling.sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering }
            )
        }
        verifyNoMoreInteractions(behandlingRepoMock, personOppslagMock, utbetalingServiceMock)
    }

    @Test
    fun `lager brevutkast for avslag`() {
        val pdf = "pdf-doc".toByteArray()
        val behandlingMock = mock<Behandling>() {
            on { erInnvilget() } doReturn false
        }
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandlingMock
        }
        val brevServiceMock = mock<BrevService>() {
            on { lagBrev(any()) } doReturn pdf.right()
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock
        ).lagBrevutkast(UUID.randomUUID())

        response shouldBe pdf.right()
        verify(brevServiceMock).lagBrev(argThat { it.shouldBeTypeOf<LagBrevRequest.AvslagsVedtak>() })
    }

    @Test
    fun `lager brevutkast for innvilgelse`() {
        val pdf = "pdf-doc".toByteArray()
        val behandlingMock = mock<Behandling>() {
            on { erInnvilget() } doReturn true
        }
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandlingMock
        }
        val brevServiceMock = mock<BrevService>() {
            on { lagBrev(any()) } doReturn pdf.right()
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock
        ).lagBrevutkast(UUID.randomUUID())

        response shouldBe pdf.right()
        verify(brevServiceMock).lagBrev(argThat { it.shouldBeTypeOf<LagBrevRequest.InnvilgetVedtak>() })
    }

    @Test
    fun `svarer med feil dersom behandling ikke finnes`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn null
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
        ).lagBrevutkast(UUID.randomUUID())

        response shouldBe KunneIkkeLageBrevutkast.FantIkkeBehandling.left()
    }

    @Test
    fun `svarer med feil dersom laging av brev feiler`() {
        val behandlingMock = mock<Behandling>() {
            on { erInnvilget() } doReturn true
        }
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandlingMock
        }
        val brevServiceMock = mock<BrevService>() {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock
        ).lagBrevutkast(UUID.randomUUID())

        response shouldBe KunneIkkeLageBrevutkast.KunneIkkeLageBrev.left()
    }

    private fun beregnetBehandling() = BehandlingFactory(mock()).createBehandling(
        sakId = sakId,
        søknad = Søknad.Journalført.MedOppgave(
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = journalpostId,
        ),
        status = Behandling.BehandlingsStatus.BEREGNET_INNVILGET,
        beregning = beregning,
        fnr = fnr,
        oppgaveId = oppgaveId
    )

    private fun behandlingTilAttestering(status: Behandling.BehandlingsStatus) = beregnetBehandling().copy(
        simulering = simulering,
        status = status
    )

    private val attestant = Attestant("SU")

    private val oppdragsmelding = Utbetalingsrequest(
        value = ""
    )

    private val beregning = BeregningFactory.ny(
        periode = Periode(1.januar(2020), 31.januar(2020)),
        sats = Sats.HØY,
        fradrag = listOf()
    )

    private val oppdrag = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = sakId,
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
}
