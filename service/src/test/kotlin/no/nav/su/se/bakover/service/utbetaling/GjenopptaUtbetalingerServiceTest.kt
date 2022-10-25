package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nåtidForSimuleringStub
import no.nav.su.se.bakover.test.simulerGjenopptak
import no.nav.su.se.bakover.test.simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.LocalDate
import java.util.UUID

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
            gjenopptak = null,
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
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
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

            verifyNoMoreInteractions(
                serviceAndMocks.sakService,
                serviceAndMocks.simuleringClient,
                serviceAndMocks.utbetalingRepo,
                serviceAndMocks.utbetalingPublisher,
            )
        }
    }

    @Test
    fun `Utbetaling feilet`() {
        val tikkendeKlokke = TikkendeKlokke(nåtidForSimuleringStub)

        val (sak, simulertGjenopptak) = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
            clock = tikkendeKlokke,
        )

        val simulertUtbetaling = simulerGjenopptak(
            sak = sak,
            gjenopptak = simulertGjenopptak,
            clock = tikkendeKlokke,
        ).getOrFail()

        UtbetalingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
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
                serviceAndMocks.simuleringClient,
                serviceAndMocks.utbetalingPublisher,
                serviceAndMocks.utbetalingRepo,
            ) {
                verify(serviceAndMocks.utbetalingPublisher).generateRequest(argThat { it shouldBe simulertUtbetaling })
                verify(serviceAndMocks.utbetalingRepo).opprettUtbetaling(any(), any())
                verify(serviceAndMocks.utbetalingPublisher).publishRequest(utbetalingsRequest)
            }
            verifyNoMoreInteractions(
                serviceAndMocks.sakService,
                serviceAndMocks.simuleringClient,
                serviceAndMocks.utbetalingRepo,
                serviceAndMocks.utbetalingPublisher,
            )
        }
    }
}
