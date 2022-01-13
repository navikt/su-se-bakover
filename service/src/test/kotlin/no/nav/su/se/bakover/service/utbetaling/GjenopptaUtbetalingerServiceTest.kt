package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknadsbehandling.simulering
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.gjenopptakUtbetalingForSimulering
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.plus
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class GjenopptaUtbetalingerServiceTest {

    @Test
    fun `Utbetalinger som er stanset blir startet igjen`() {
        val periode = Periode.create(
            fraOgMed = fixedLocalDate.plusMonths(1).startOfMonth(),
            tilOgMed = periode2021.tilOgMed,
        )

        val (sak, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
            clock = fixedClock,
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()

        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on { publish(any()) } doReturn utbetalingsRequest.right()
        }

        val simulering = simuleringGjenopptak(
            eksisterendeUtbetalinger = sak.utbetalinger,
            fnr = sak.fnr,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            clock = fixedClock.plus(1, ChronoUnit.SECONDS),
        )

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            clock = fixedClock.plus(2, ChronoUnit.SECONDS),
        ).gjenopptaUtbetalinger(
            sakId = sak.id,
            attestant = saksbehandler,
            simulering = simulering,
        ).getOrFail("skulle gått bra").let {
            inOrder(
                sakServiceMock,
                simuleringClientMock,
                utbetalingPublisherMock,
                utbetalingRepoMock,
            ) {
                verify(sakServiceMock, times(2)).hentSak(
                    sakId = argThat { it shouldBe sak.id },
                )
                verify(simuleringClientMock).simulerUtbetaling(
                    argThat {
                        it.utbetalingslinjer shouldHaveSize 1
                        val utbetalingslinjeForStans = sak.utbetalinger.first().sisteUtbetalingslinje()
                        it.utbetalingslinjer.first().shouldBeEqualToIgnoringFields(
                            Utbetalingslinje.Endring.Reaktivering(
                                utbetalingslinje = utbetalingslinjeForStans,
                                virkningstidspunkt = periode.fraOgMed,
                                clock = fixedClock,
                            ),
                            Utbetalingslinje.Endring.Reaktivering::id,
                            Utbetalingslinje.Endring.Reaktivering::opprettet,
                        )
                    },
                )
                verify(utbetalingPublisherMock).publish(any())
                verify(utbetalingRepoMock).opprettUtbetaling(any())
            }
            verifyNoMoreInteractions(
                sakServiceMock,
                simuleringClientMock,
                utbetalingRepoMock,
                utbetalingPublisherMock,
            )
        }
    }

    @Test
    fun `Fant ikke sak`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
        }
        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()
        val simuleringClientMock = mock<SimuleringClient>()
        val service = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            clock = fixedClock,
        )

        service.gjenopptaUtbetalinger(
            sakId = sakId,
            attestant = saksbehandler,
            simulering = simulering,
        ).let {
            it shouldBe UtbetalGjenopptakFeil.KunneIkkeUtbetale(UtbetalingFeilet.FantIkkeSak).left()

            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verifyNoMoreInteractions(sakServiceMock, utbetalingRepoMock, utbetalingPublisherMock, simuleringClientMock)
        }
    }

    @Test
    fun `Simulering feiler`() {
        val (sak, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak()

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            clock = fixedClock,
        ).gjenopptaUtbetalinger(
            sakId = sak.id,
            attestant = saksbehandler,
            simulering = simulering,
        ).let {
            it shouldBe UtbetalGjenopptakFeil.KunneIkkeSimulere(SimulerGjenopptakFeil.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL))
                .left()

            inOrder(sakServiceMock, simuleringClientMock) {
                verify(sakServiceMock, times(2)).hentSak(sak.id)
                verify(simuleringClientMock).simulerUtbetaling(
                    argThat { it shouldBe beOfType<Utbetaling.UtbetalingForSimulering>() },
                )
            }
            verifyNoMoreInteractions(
                sakServiceMock,
                simuleringClientMock,
                utbetalingRepoMock,
                utbetalingPublisherMock,
            )
        }
    }

    @Test
    fun `Utbetaling feilet`() {
        val (sak, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = fixedClock
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val simulering = simuleringGjenopptak(
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = fixedClock.plus(2, ChronoUnit.SECONDS),
        )
        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()

        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on {
                publish(any())
            } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(utbetalingsRequest).left()
        }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            clock = fixedClock.plus(3, ChronoUnit.SECONDS),
        ).gjenopptaUtbetalinger(
            sakId = sak.id,
            attestant = saksbehandler,
            simulering = simulering,
        ).let {
            it shouldBe UtbetalGjenopptakFeil.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil).left()

            inOrder(sakServiceMock, simuleringClientMock, utbetalingPublisherMock) {
                verify(sakServiceMock, times(2)).hentSak(sakId = argThat { sak.id })
                verify(simuleringClientMock).simulerUtbetaling(argThat { gjenopptakUtbetalingForSimulering() })
                verify(utbetalingPublisherMock).publish(argThat { simulertGjenopptakUtbetaling() })
            }
            verifyNoMoreInteractions(
                sakServiceMock,
                simuleringClientMock,
                utbetalingRepoMock,
                utbetalingRepoMock,
            )
        }
    }

    @Test
    fun `svarer med feil dersom kontroll av simulering ikke går bra`() {
        val (sak, revurdering) = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse()

        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringMedFeil = Simulering(
            gjelderId = fnr,
            gjelderNavn = "navn",
            datoBeregnet = idag(fixedClock),
            nettoBeløp = 15000,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = revurdering.periode.tilOgMed,
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = "",
                            utbetalesTilId = Fnr.generer(),
                            utbetalesTilNavn = "",
                            forfall = 1.januar(2020),
                            feilkonto = false,
                            detaljer = listOf(
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.januar(2021),
                                    faktiskTilOgMed = 31.januar(2021),
                                    konto = "konto",
                                    belop = 20779,
                                    tilbakeforing = false,
                                    sats = 20779,
                                    typeSats = "MND",
                                    antallSats = 1,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = "suBeskrivelse",
                                    klasseType = KlasseType.YTEL,
                                ),
                                // Dobbeltutbetaling
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.januar(2021),
                                    faktiskTilOgMed = 31.januar(2021),
                                    konto = "konto",
                                    belop = 20779,
                                    tilbakeforing = false,
                                    sats = 20779,
                                    typeSats = "MND",
                                    antallSats = 1,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = "suBeskrivelse",
                                    klasseType = KlasseType.YTEL,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simuleringMedFeil.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()

        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).gjenopptaUtbetalinger(
            sakId = sak.id,
            attestant = attestant,
            simulering = simuleringMedFeil,
        ) shouldBe UtbetalGjenopptakFeil.KunneIkkeUtbetale(UtbetalingFeilet.KontrollAvSimuleringFeilet).left()

        verifyNoMoreInteractions(utbetalingRepoMock, utbetalingPublisherMock)
    }
}
