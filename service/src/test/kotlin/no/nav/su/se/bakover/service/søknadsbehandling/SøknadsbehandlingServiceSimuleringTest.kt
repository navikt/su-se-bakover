package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.beregnetSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nyUtbetalingSimulert
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

internal class SøknadsbehandlingServiceSimuleringTest {

    private val sakOgBehandling = beregnetSøknadsbehandlingUføre()
    private val beregnetBehandling = sakOgBehandling.second

    @Test
    fun `simuler behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn beregnetBehandling
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any()) } doReturn nyUtbetalingSimulert(
                sakOgBehandling = sakOgBehandling,
                beregning = beregnetBehandling.beregning,
                clock = fixedClock,
            ).right()
        }
        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(
            SøknadsbehandlingService.SimulerRequest(
                behandlingId = beregnetBehandling.id,
                saksbehandler = saksbehandler,
            ),
        ).getOrFail()

        response.shouldBeType<Søknadsbehandling.Simulert>()

        verify(søknadsbehandlingRepoMock).hent(beregnetBehandling.id)
        verify(utbetalingServiceMock).simulerUtbetaling(
            request = SimulerUtbetalingRequest.NyUtbetaling.Uføre(
                sakId = beregnetBehandling.sakId,
                saksbehandler = saksbehandler,
                beregning = beregnetBehandling.beregning,
                uføregrunnlag = beregnetBehandling.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
                utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            ),
        )
        verify(søknadsbehandlingRepoMock).lagre(response)
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
            on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TekniskFeil.left()
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(
            SøknadsbehandlingService.SimulerRequest(beregnetBehandling.id, saksbehandler),
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeSimulereBehandling.KunneIkkeSimulere(
            KunneIkkeSimulereBehandling.KunneIkkeSimulere(SimuleringFeilet.TekniskFeil),
        ).left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe beregnetBehandling.id })

        verify(utbetalingServiceMock).simulerUtbetaling(
            request = SimulerUtbetalingRequest.NyUtbetaling.Uføre(
                sakId = beregnetBehandling.sakId,
                saksbehandler = saksbehandler,
                beregning = beregnetBehandling.beregning,
                uføregrunnlag = beregnetBehandling.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
                utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            ),
        )
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, utbetalingServiceMock)
    }
}
