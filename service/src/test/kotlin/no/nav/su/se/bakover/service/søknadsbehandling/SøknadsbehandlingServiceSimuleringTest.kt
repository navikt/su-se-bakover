package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingskjøreplan
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetalingslinje
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class SøknadsbehandlingServiceSimuleringTest {

    @Test
    fun `simuler behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn beregnetBehandling
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any()) } doReturn simulertUtbetaling.right()
        }
        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(
            SøknadsbehandlingService.SimulerRequest(beregnetBehandling.id, saksbehandler),
        )

        val expected = Søknadsbehandling.Simulert(
            id = beregnetBehandling.id,
            opprettet = beregnetBehandling.opprettet,
            behandlingsinformasjon = beregnetBehandling.behandlingsinformasjon,
            søknad = beregnetBehandling.søknad,
            beregning = beregnetBehandling.beregning,
            sakId = beregnetBehandling.sakId,
            saksnummer = beregnetBehandling.saksnummer,
            fnr = beregnetBehandling.fnr,
            oppgaveId = beregnetBehandling.oppgaveId,
            simulering = simulering,
            fritekstTilBrev = "",
            stønadsperiode = beregnetBehandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
        )

        response shouldBe expected.right()

        verify(søknadsbehandlingRepoMock).hent(beregnetBehandling.id)
        verify(utbetalingServiceMock).simulerUtbetaling(
            request = SimulerUtbetalingRequest.NyUtbetaling(
                sakId = beregnetBehandling.sakId,
                saksbehandler = saksbehandler,
                beregning = beregnetBehandling.beregning,
                uføregrunnlag = emptyList(),
                kjøreplan = Utbetalingskjøreplan.NEI,
            ),
        )
        verify(søknadsbehandlingRepoMock).lagre(expected)
    }

    @Test
    fun `simuler behandling gir feilmelding hvis vi ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }
        val utbetalingServiceMock = mock<UtbetalingService>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(
            SøknadsbehandlingService.SimulerRequest(beregnetBehandling.id, saksbehandler),
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeSimulereBehandling.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe beregnetBehandling.id })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, utbetalingServiceMock)
    }

    @Test
    fun `simuler behandling gir feilmelding hvis simulering ikke går bra`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn beregnetBehandling
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(
            SøknadsbehandlingService.SimulerRequest(beregnetBehandling.id, saksbehandler),
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeSimulereBehandling.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL)
            .left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe beregnetBehandling.id })

        verify(utbetalingServiceMock).simulerUtbetaling(
            request = SimulerUtbetalingRequest.NyUtbetaling(
                sakId = beregnetBehandling.sakId,
                saksbehandler = saksbehandler,
                beregning = beregnetBehandling.beregning,
                uføregrunnlag = emptyList(),
                kjøreplan = Utbetalingskjøreplan.NEI,
            ),
        )
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, utbetalingServiceMock)
    }

    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val fnr = Fnr("12345678910")
    private val saksbehandler = Saksbehandler("AB12345")
    private val oppgaveId = OppgaveId("o")
    private val stønadsperiode = Stønadsperiode.create(år(2021))
    private val beregnetBehandling = Søknadsbehandling.Beregnet.Innvilget(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = JournalpostId("j"),
        ),
        beregning = TestBeregning,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        oppgaveId = oppgaveId,
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
        attesteringer = Attesteringshistorikk.empty(),
        avkorting = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
    )

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(fixedClock),
        nettoBeløp = 191500,
        periodeList = listOf(),
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,
        utbetalingslinjer = nonEmptyListOf(
            utbetalingslinje(
                periode = januar(2021),
                beløp = 0,
            ),
        ),
        fnr = fnr,
        type = Utbetaling.UtbetalingsType.NY,
        behandler = Attestant("SU"),
        avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt),
    )

    private val simulertUtbetaling = utbetalingForSimulering.toSimulertUtbetaling(simulering)
}
