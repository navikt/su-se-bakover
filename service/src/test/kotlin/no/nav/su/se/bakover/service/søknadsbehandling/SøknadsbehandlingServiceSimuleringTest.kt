package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.beregnetSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.nyUtbetalingSimulert
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID

internal class SøknadsbehandlingServiceSimuleringTest {

    @Test
    fun `simuler behandling`() {
        val (sak, beregnet) = beregnetSøknadsbehandlingUføre()

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSakForSøknadsbehandling(any()) } doReturn sak
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn nyUtbetalingSimulert(
                    sakOgBehandling = sak to beregnet,
                    beregning = beregnet.beregning,
                    clock = fixedClock,
                ).right()
            },
            søknadsbehandlingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
        ).also {
            val response = it.søknadsbehandlingService.simuler(
                SøknadsbehandlingService.SimulerRequest(
                    behandlingId = beregnet.id,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail()

            response.shouldBeType<Søknadsbehandling.Simulert>()

            verify(it.sakService).hentSakForSøknadsbehandling(beregnet.id)
            verify(it.utbetalingService, times(2)).simulerUtbetaling(
                utbetaling = any(),
                simuleringsperiode = argThat { it shouldBe beregnet.periode },
            )
            verify(it.søknadsbehandlingRepo).lagre(response)
        }
    }

    @Test
    fun `simuler behandling gir feilmelding hvis vi ikke finner behandling`() {
        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSakForSøknadsbehandling(any()) } doReturn nySakUføre().first
            },
        ).also {
            val response = it.søknadsbehandlingService.simuler(
                SøknadsbehandlingService.SimulerRequest(UUID.randomUUID(), saksbehandler),
            )
            response shouldBe SøknadsbehandlingService.KunneIkkeSimulereBehandling.FantIkkeBehandling.left()

            verify(it.sakService).hentSakForSøknadsbehandling(any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `simuler behandling gir feilmelding hvis simulering ikke går bra`() {
        val (sak, beregnet) = beregnetSøknadsbehandlingUføre()

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSakForSøknadsbehandling(any()) } doReturn sak
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn SimuleringFeilet.TekniskFeil.left()
            },
        ).also {
            val response = it.søknadsbehandlingService.simuler(
                SøknadsbehandlingService.SimulerRequest(beregnet.id, saksbehandler),
            )

            response shouldBe SøknadsbehandlingService.KunneIkkeSimulereBehandling.KunneIkkeSimulere(
                KunneIkkeSimulereBehandling.KunneIkkeSimulere(SimulerUtbetalingFeilet.FeilVedSimulering(SimuleringFeilet.TekniskFeil)),
            ).left()

            verify(it.sakService).hentSakForSøknadsbehandling(argThat { it shouldBe beregnet.id })

            verify(it.utbetalingService).simulerUtbetaling(
                utbetaling = any(),
                simuleringsperiode = argThat { it shouldBe beregnet.periode },
            )
            it.verifyNoMoreInteractions()
        }
    }
}
