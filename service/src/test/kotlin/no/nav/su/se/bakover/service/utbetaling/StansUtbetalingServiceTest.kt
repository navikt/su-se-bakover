package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForStans
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulerStans
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.stønadsperiode2022
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock

internal class StansUtbetalingServiceTest {

    @Test
    fun `stans utbetalinger`() {
        val tikkendeKlokke = TikkendeKlokke(1.januar(2022).fixedClock())
        val (sak, simulertStans) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = år(2022),
            clock = tikkendeKlokke,
        )
        UtbetalingServiceAndMocks(
            utbetalingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            utbetalingPublisher = mock {
                on { generateRequest(any()) } doReturn utbetalingsRequest
                on { publishRequest(any()) } doReturn utbetalingsRequest.right()
            },
            clock = tikkendeKlokke,
        ).also { serviceAndMocks ->
            serviceAndMocks.service.klargjørUtbetaling(
                utbetaling = simulerStans(
                    sak = sak,
                    stans = simulertStans,
                    stansDato = simulertStans.periode.fraOgMed,
                    behandler = saksbehandler,
                    clock = tikkendeKlokke,
                ).getOrFail(),
                transactionContext = TestSessionFactory.transactionContext,
            ).getOrFail().shouldBeType<UtbetalingKlargjortForOversendelse<UtbetalStansFeil>>().sendUtbetaling() shouldBe utbetalingsRequest.right()

            inOrder(
                *serviceAndMocks.allMocks(),
            ) {
                verify(serviceAndMocks.utbetalingPublisher).generateRequest(any())
                verify(serviceAndMocks.utbetalingRepo).opprettUtbetaling(any(), anyOrNull())
                verify(serviceAndMocks.utbetalingPublisher).publishRequest(any())
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når simulering feiler`() {
        val tikkendeKlokke = TikkendeKlokke(1.januar(2022).fixedClock())
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = stønadsperiode2022,
            clock = tikkendeFixedClock(),
        )

        UtbetalingServiceAndMocks(
            simuleringClient = mock {
                on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TekniskFeil.left()
            },
            clock = tikkendeKlokke,
        ).also {
            it.service.simulerUtbetaling(
                utbetaling = sak.lagUtbetalingForStans(
                    stansdato = 1.januar(2022),
                    behandler = saksbehandler,
                    clock = tikkendeKlokke,
                ).getOrFail(),
                simuleringsperiode = år(2022),
            ) shouldBe SimuleringFeilet.TekniskFeil.left()

            inOrder(
                *it.allMocks(),
            ) {
                verify(it.simuleringClient).simulerUtbetaling(any())
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når utbetaling feiler`() {
        val tikkendeKlokke = TikkendeKlokke(1.januar(2022).fixedClock())
        val (sak, stans) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = år(2022),
            clock = tikkendeKlokke,
        )
        UtbetalingServiceAndMocks(
            utbetalingPublisher = mock {
                on { generateRequest(any()) } doReturn utbetalingsRequest
                on { publishRequest(any()) } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(utbetalingsRequest).left()
            },
            clock = tikkendeKlokke,
        ).also { serviceAndMocks ->
            serviceAndMocks.service.klargjørUtbetaling(
                utbetaling = simulerStans(
                    sak = sak,
                    stans = stans,
                    stansDato = stans.periode.fraOgMed,
                    behandler = stans.saksbehandler,
                    clock = tikkendeKlokke,
                ).getOrFail(),
                transactionContext = TestSessionFactory.transactionContext,
            ).let {
                it.getOrFail().shouldBeType<UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>().let {
                    it.sendUtbetaling() shouldBe UtbetalingFeilet.Protokollfeil.left()
                }
            }

            inOrder(
                *serviceAndMocks.allMocks(),
            ) {
                verify(serviceAndMocks.utbetalingPublisher).generateRequest(any())
                verify(serviceAndMocks.utbetalingRepo).opprettUtbetaling(any(), argThat { TestSessionFactory.transactionContext })
                verify(serviceAndMocks.utbetalingPublisher).publishRequest(any())
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }
}
