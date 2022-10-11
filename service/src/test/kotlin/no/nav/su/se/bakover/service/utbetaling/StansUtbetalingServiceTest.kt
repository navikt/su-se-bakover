package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.toPeriode
import no.nav.su.se.bakover.domain.oppdrag.FeilVedKryssjekkAvTidslinjerOgSimulering
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForStans
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simuleringStans
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

internal class StansUtbetalingServiceTest {

    @Test
    fun `stans utbetalinger`() {
        val (sak, simulertStans) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak()
        UtbetalingServiceAndMocks(
            simuleringClient = mock {
                on { simulerUtbetaling(any()) } doReturn simulertStans.simulering.right()
            },
            utbetalingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            utbetalingPublisher = mock {
                on { generateRequest(any()) } doReturn utbetalingsRequest
                on { publishRequest(any()) } doReturn utbetalingsRequest.right()
            },
        ).also { serviceAndMocks ->
            serviceAndMocks.service.klargjørStans(
                utbetaling = sak.lagUtbetalingForStans(
                    stansdato = 1.februar(2021),
                    behandler = saksbehandler,
                    clock = fixedClock,
                ).getOrFail(),
                saksbehandlersSimulering = simulertStans.simulering,
                transactionContext = TestSessionFactory.transactionContext,
            ).getOrFail().shouldBeType<UtbetalingKlargjortForOversendelse<UtbetalStansFeil>>().sendUtbetaling()

            inOrder(
                serviceAndMocks.simuleringClient,
                serviceAndMocks.utbetalingPublisher,
                serviceAndMocks.utbetalingRepo,
            ) {
                verify(serviceAndMocks.simuleringClient).simulerUtbetaling(any())
                verify(serviceAndMocks.utbetalingPublisher).generateRequest(any())
                verify(serviceAndMocks.utbetalingRepo).opprettUtbetaling(any(), anyOrNull())
                verify(serviceAndMocks.utbetalingPublisher).publishRequest(any())
            }
            verifyNoMoreInteractions(
                serviceAndMocks.simuleringClient,
                serviceAndMocks.utbetalingRepo,
                serviceAndMocks.utbetalingPublisher,
            )
        }
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når simulering feiler`() {
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()
        val simulering = simuleringStans(
            stansDato = 1.februar(2021),
            eksisterendeUtbetalinger = sak.utbetalinger,
            fnr = sak.fnr,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            clock = fixedClock,
        )
        UtbetalingServiceAndMocks(
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            simuleringClient = mock {
                on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TekniskFeil.left()
            },
        ).also {
            it.service.klargjørStans(
                utbetaling = sak.lagUtbetalingForStans(
                    stansdato = 1.februar(2021),
                    behandler = saksbehandler,
                    clock = fixedClock,
                ).getOrFail(),
                saksbehandlersSimulering = simulering,
                transactionContext = TestSessionFactory.transactionContext,
            ) shouldBe UtbetalStansFeil.KunneIkkeSimulere(SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TekniskFeil)).left()

            inOrder(it.simuleringClient) {
                verify(it.simuleringClient).simulerUtbetaling(any())
            }
            verifyNoMoreInteractions(
                it.sakService,
                it.simuleringClient,
                it.utbetalingRepo,
                it.utbetalingPublisher,
            )
        }
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når utbetaling feiler`() {
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()
        val simulering = simuleringStans(
            stansDato = 1.februar(2021),
            eksisterendeUtbetalinger = sak.utbetalinger,
            fnr = sak.fnr,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            clock = fixedClock,
        )
        UtbetalingServiceAndMocks(
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            simuleringClient = mock {
                on { simulerUtbetaling(any()) } doReturn simulering.right()
            },
            utbetalingPublisher = mock {
                on { generateRequest(any()) } doReturn utbetalingsRequest
                on { publishRequest(any()) } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(utbetalingsRequest).left()
            },
        ).also { serviceAndMocks ->
            serviceAndMocks.service.klargjørStans(
                utbetaling = sak.lagUtbetalingForStans(
                    stansdato = 1.februar(2021),
                    behandler = saksbehandler,
                    clock = fixedClock,
                ).getOrFail(),
                saksbehandlersSimulering = simulering,
                transactionContext = TestSessionFactory.transactionContext,
            ).let {
                it.getOrFail().shouldBeType<UtbetalingKlargjortForOversendelse<UtbetalStansFeil>>().let {
                    it.sendUtbetaling() shouldBe UtbetalStansFeil.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil).left()
                }
            }

            inOrder(
                serviceAndMocks.sakService,
                serviceAndMocks.simuleringClient,
                serviceAndMocks.utbetalingRepo,
                serviceAndMocks.utbetalingPublisher,
                serviceAndMocks.utbetalingRepo,
            ) {
                verify(serviceAndMocks.simuleringClient).simulerUtbetaling(any())
                verify(serviceAndMocks.utbetalingPublisher).generateRequest(any())
                verify(serviceAndMocks.utbetalingRepo).opprettUtbetaling(any(), argThat { TestSessionFactory.transactionContext })
                verify(serviceAndMocks.utbetalingPublisher).publishRequest(any())
            }
            verifyNoMoreInteractions(
                serviceAndMocks.sakService,
                serviceAndMocks.simuleringClient,
                serviceAndMocks.utbetalingRepo,
                serviceAndMocks.utbetalingPublisher,
            )
        }
    }

    @Test
    fun `feiler dersom stans fører til feilutbetaling`() {
        val (sak, revurdering) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak()
        val stansdato = 1.februar(2021)

        // attestants simulering svarer med feilutbetalinger
        val simuleringMedProblemer = simuleringFeilutbetaling(
            perioder = stansdato.rangeTo(revurdering.periode.tilOgMed).toPeriode().måneder().toTypedArray(),
        )

        UtbetalingServiceAndMocks(
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            simuleringClient = mock {
                on { simulerUtbetaling(any()) } doReturn simuleringMedProblemer.right()
            },
        ).also {
            it.service.klargjørStans(
                utbetaling = sak.lagUtbetalingForStans(
                    stansdato = stansdato,
                    behandler = saksbehandler,
                    clock = fixedClock,
                ).getOrFail(),
                saksbehandlersSimulering = revurdering.simulering,
                transactionContext = TestSessionFactory.transactionContext,
            ) shouldBe UtbetalStansFeil.KunneIkkeSimulere(SimulerStansFeilet.KontrollFeilet(FeilVedKryssjekkAvTidslinjerOgSimulering.Stans.SimuleringHarFeilutbetaling)).left()

            verify(it.utbetalingPublisher, never()).publishRequest(any())
            verify(it.utbetalingRepo, never()).opprettUtbetaling(any(), anyOrNull())
        }
    }
}
