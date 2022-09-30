package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.gjenopptakUtbetalingForSimulering
import no.nav.su.se.bakover.test.nåtidForSimuleringStub
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simuleringGjenopptak
import no.nav.su.se.bakover.test.simulertGjenopptakUtbetaling
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

        val simulering = simuleringGjenopptak(
            eksisterendeUtbetalinger = sak.utbetalinger,
            fnr = sak.fnr,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
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
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            simuleringClient = mock {
                on { simulerUtbetaling(any()) } doReturn simulering.right()
            },
            clock = tikkendeKlokke,
        ).also { serviceAndMocks ->
            serviceAndMocks.service.klargjørGjenopptak(
                request = UtbetalRequest.Gjenopptak(
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    simulering = simulering,
                ),
                transactionContext = TestSessionFactory.transactionContext,
            ).getOrFail().let {
                it.sendUtbetaling()
            }

            verify(serviceAndMocks.sakService).hentSak(
                sakId = argThat { it shouldBe sak.id },
            )
            verify(serviceAndMocks.simuleringClient).simulerUtbetaling(
                argThat {
                    it.utbetaling.utbetalingslinjer shouldHaveSize 1
                    val utbetalingslinjeForStans = sak.utbetalinger.first().sisteUtbetalingslinje()
                    it.utbetaling.utbetalingslinjer.first().shouldBeEqualToIgnoringFields(
                        Utbetalingslinje.Endring.Reaktivering(
                            utbetalingslinje = utbetalingslinjeForStans,
                            virkningstidspunkt = periode.fraOgMed,
                            clock = tikkendeKlokke,
                        ),
                        Utbetalingslinje.Endring.Reaktivering::id,
                        Utbetalingslinje.Endring.Reaktivering::opprettet,
                    )
                },
            )
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
    fun `Fant ikke sak`() {
        UtbetalingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
            },
        ).also {
            it.service.klargjørGjenopptak(
                request = UtbetalRequest.Gjenopptak(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    simulering = mock(),
                ),
                transactionContext = TestSessionFactory.transactionContext,
            ) shouldBe UtbetalGjenopptakFeil.KunneIkkeUtbetale(UtbetalingFeilet.FantIkkeSak).left()
        }
    }

    @Test
    fun `Simulering feiler`() {
        val (sak, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak()

        UtbetalingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            simuleringClient = mock {
                on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TekniskFeil.left()
            },
        ).also {
            it.service.klargjørGjenopptak(
                request = UtbetalRequest.Gjenopptak(
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    simulering = simuleringGjenopptak(
                        eksisterendeUtbetalinger = sak.utbetalinger,
                        fnr = sak.fnr,
                        sakId = sak.id,
                        saksnummer = sak.saksnummer,
                        clock = fixedClock,
                    ),
                ),
                transactionContext = TestSessionFactory.transactionContext,
            ) shouldBe UtbetalGjenopptakFeil.KunneIkkeSimulere(SimulerGjenopptakFeil.KunneIkkeSimulere(SimuleringFeilet.TekniskFeil)).left()

            inOrder(it.sakService, it.simuleringClient) {
                verify(it.sakService).hentSak(sak.id)
                verify(it.simuleringClient).simulerUtbetaling(
                    argThat { it shouldBe beOfType<SimulerUtbetalingForPeriode>() },
                )
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
    fun `Utbetaling feilet`() {
        val klokke = TikkendeKlokke(nåtidForSimuleringStub)

        val (sak, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = klokke,
        )

        val simulering = simuleringGjenopptak(
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = klokke,
        )

        UtbetalingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            simuleringClient = mock {
                on { simulerUtbetaling(any()) } doReturn simulering.right()
            },
            utbetalingPublisher = mock {
                on { generateRequest(any()) } doReturn utbetalingsRequest
                on { publishRequest(any()) } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(utbetalingsRequest).left()
            },
            clock = klokke,
        ).also { serviceAndMocks ->
            serviceAndMocks.service.klargjørGjenopptak(
                request = UtbetalRequest.Gjenopptak(
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    simulering = simulering,
                ),
                transactionContext = TestSessionFactory.transactionContext,
            ).getOrFail().let {
                it.sendUtbetaling() shouldBe UtbetalGjenopptakFeil.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil).left()
            }

            inOrder(
                serviceAndMocks.sakService,
                serviceAndMocks.simuleringClient,
                serviceAndMocks.utbetalingPublisher,
                serviceAndMocks.utbetalingRepo,
            ) {
                verify(serviceAndMocks.sakService).hentSak(sakId = argThat { sak.id })
                verify(serviceAndMocks.simuleringClient).simulerUtbetaling(argThat { gjenopptakUtbetalingForSimulering() })
                verify(serviceAndMocks.utbetalingPublisher).generateRequest(argThat { simulertGjenopptakUtbetaling() })
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

    @Test
    fun `svarer med feil dersom kontroll av simulering ikke går bra`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClock)
        val (sak, revurdering) = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
            clock = tikkendeKlokke,
        )

        val simuleringMedFeil = simulertGjenopptakUtbetaling(
            fnr = Fnr.generer(), // nytt fnr
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            clock = tikkendeKlokke,
            eksisterendeUtbetalinger = sak.utbetalinger,
        )
        UtbetalingServiceAndMocks(
            sakService = mock {
                on { hentSak(sak.id) } doReturn sak.right()
            },
            simuleringClient = mock {
                on { simulerUtbetaling(any()) } doReturn simuleringMedFeil.simulering.right()
            },
            utbetalingPublisher = mock(),
            clock = tikkendeKlokke,
        ).also {
            it.service.klargjørGjenopptak(
                request = UtbetalRequest.Gjenopptak(
                    sakId = sak.id,
                    saksbehandler = attestant,
                    simulering = revurdering.simulering,
                ),
                transactionContext = TestSessionFactory.transactionContext,
            ) shouldBe UtbetalGjenopptakFeil.KunneIkkeUtbetale(UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikGjelderId)).left()
            verifyNoMoreInteractions(it.utbetalingRepo, it.utbetalingPublisher)
        }
    }
}
