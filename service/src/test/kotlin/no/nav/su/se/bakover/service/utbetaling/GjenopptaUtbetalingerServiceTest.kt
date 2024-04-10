package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nåtidForSimuleringStub
import no.nav.su.se.bakover.test.simulering.simulerGjenopptak
import no.nav.su.se.bakover.test.simulertGjenopptakAvYtelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.utbetaling.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingPublisher
import java.time.LocalDate

internal class GjenopptaUtbetalingerServiceTest {

    @Test
    fun `Utbetalinger som er stanset blir startet igjen`() {
        val tikkendeKlokke = TikkendeKlokke(nåtidForSimuleringStub)
        val periode = Periode.create(
            fraOgMed = LocalDate.now(tikkendeKlokke).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )

        val (sak, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
            clock = tikkendeKlokke,
        )

        val simulertUtbetaling = simulerGjenopptak(
            sak = sak,
            clock = tikkendeKlokke,
        ).getOrFail()

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
                utbetaling = simulertUtbetaling,
                transactionContext = TestSessionFactory.transactionContext,
            ).getOrFail().sendUtbetaling()

            verify(serviceAndMocks.utbetalingPublisher).generateRequest(any())
            verify(serviceAndMocks.utbetalingRepo).opprettUtbetaling(any(), argThat { it shouldBe TestSessionFactory.transactionContext })
            verify(serviceAndMocks.utbetalingPublisher).publishRequest(argThat { it shouldBe utbetalingsRequest })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `Utbetaling feilet`() {
        val tikkendeKlokke = TikkendeKlokke(nåtidForSimuleringStub)

        val (sak, _) = simulertGjenopptakAvYtelseFraVedtakStansAvYtelse(
            clock = tikkendeKlokke,
        )

        val simulertUtbetaling = simulerGjenopptak(
            sak = sak,
            clock = tikkendeKlokke,
        ).getOrFail()

        UtbetalingServiceAndMocks(
            utbetalingPublisher = mock {
                on { generateRequest(any()) } doReturn utbetalingsRequest
                on { publishRequest(any()) } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(utbetalingsRequest).left()
            },
            clock = tikkendeKlokke,
        ).also { serviceAndMocks ->
            serviceAndMocks.service.klargjørUtbetaling(
                utbetaling = simulertUtbetaling,
                transactionContext = TestSessionFactory.transactionContext,
            ).getOrFail().let {
                it.sendUtbetaling() shouldBe UtbetalingFeilet.Protokollfeil.left()
            }

            inOrder(
                *serviceAndMocks.allMocks(),
            ) {
                verify(serviceAndMocks.utbetalingPublisher).generateRequest(argThat { it shouldBe simulertUtbetaling })
                verify(serviceAndMocks.utbetalingRepo).opprettUtbetaling(any(), any())
                verify(serviceAndMocks.utbetalingPublisher).publishRequest(utbetalingsRequest)
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }
}
