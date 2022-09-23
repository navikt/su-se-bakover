package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.toPeriode
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelseTilOS
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simuleringStans
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        val (sak, _) = iverksattSøknadsbehandlingUføre()
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
            utbetalingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            utbetalingPublisher = mock {
                on { generateRequest(any()) } doReturn utbetalingsRequest
                on { publishRequest(any()) } doReturn utbetalingsRequest.right()
            },
        ).also { serviceAndMocks ->
            serviceAndMocks.service.stansUtbetalinger(
                request = UtbetalRequest.Stans(
                    request = SimulerUtbetalingRequest.Stans(
                        sakId = sakId,
                        saksbehandler = saksbehandler,
                        stansdato = 1.februar(2021),
                    ),
                    simulering = simulering,
                ),
                transactionContext = TestSessionFactory.transactionContext,
            ).getOrFail().shouldBeType<UtbetalingKlargjortForOversendelseTilOS<UtbetalStansFeil>>().sendUtbetalingTilOS()

            inOrder(
                serviceAndMocks.sakService,
                serviceAndMocks.simuleringClient,
                serviceAndMocks.utbetalingPublisher,
                serviceAndMocks.utbetalingRepo,
            ) {
                verify(serviceAndMocks.sakService).hentSak(
                    sakId = argThat { it shouldBe sakId },
                )
                verify(serviceAndMocks.simuleringClient).simulerUtbetaling(any())
                verify(serviceAndMocks.utbetalingPublisher).generateRequest(any())
                verify(serviceAndMocks.utbetalingRepo).opprettUtbetaling(any(), anyOrNull())
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
                on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
            },
        ).also {
            it.service.stansUtbetalinger(
                request = UtbetalRequest.Stans(
                    request = SimulerUtbetalingRequest.Stans(
                        sakId = sakId,
                        saksbehandler = saksbehandler,
                        stansdato = 1.februar(2021),
                    ),
                    simulering = simulering,
                ),
                transactionContext = TestSessionFactory.transactionContext,
            ) shouldBe UtbetalStansFeil.KunneIkkeSimulere(SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL)).left()

            inOrder(it.sakService, it.simuleringClient) {
                verify(it.sakService).hentSak(sakId = argThat { it shouldBe sakId })
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
            serviceAndMocks.service.stansUtbetalinger(
                request = UtbetalRequest.Stans(
                    request = SimulerUtbetalingRequest.Stans(
                        sakId = sakId,
                        saksbehandler = saksbehandler,
                        stansdato = 1.februar(2021),
                    ),
                    simulering = simulering,
                ),
                transactionContext = TestSessionFactory.transactionContext,
            ).let {
                it.getOrFail().shouldBeType<UtbetalingKlargjortForOversendelseTilOS<UtbetalStansFeil>>().let {
                    it.sendUtbetalingTilOS() shouldBe UtbetalStansFeil.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil).left()
                }
            }

            inOrder(
                serviceAndMocks.sakService,
                serviceAndMocks.simuleringClient,
                serviceAndMocks.utbetalingRepo,
                serviceAndMocks.utbetalingPublisher,
                serviceAndMocks.utbetalingRepo,
            ) {
                verify(serviceAndMocks.sakService).hentSak(sakId = argThat { it shouldBe sakId })
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
            assertThrows<IllegalStateException> {
                it.service.stansUtbetalinger(
                    request = UtbetalRequest.Stans(
                        request = SimulerUtbetalingRequest.Stans(
                            sakId = sakId,
                            saksbehandler = saksbehandler,
                            stansdato = stansdato,
                        ),
                        simulering = revurdering.simulering,
                    ),
                    transactionContext = TestSessionFactory.transactionContext,
                ) shouldBe UtbetalStansFeil.KunneIkkeUtbetale(UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikFeilutbetaling)).left()

                verify(it.utbetalingPublisher, never()).publish(any())
                verify(it.utbetalingRepo, never()).opprettUtbetaling(any(), anyOrNull())
            }
        }
    }
}
