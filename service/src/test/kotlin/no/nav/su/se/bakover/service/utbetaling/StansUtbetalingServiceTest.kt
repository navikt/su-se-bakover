package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class StansUtbetalingServiceTest {
    private val fnr = Fnr("12345678910")
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val saksbehandler = NavIdentBruker.Saksbehandler("Z123")
    private val avstemmingsnøkkel = Avstemmingsnøkkel()

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = nonEmptyListOf(
            Utbetalingslinje.Ny(
                opprettet = fixedTidspunkt,
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.januar(2020),
                forrigeUtbetalingslinjeId = null,
                beløp = 0,
            ),
        ),
        type = Utbetaling.UtbetalingsType.STANS,
        behandler = NavIdentBruker.Saksbehandler("Z123"),
        avstemmingsnøkkel = avstemmingsnøkkel,
    )

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = idag(),
        nettoBeløp = 0,
        periodeList = listOf(),
    )
    private val oppdragsmelding = Utbetalingsrequest(
        value = "",
    )
    private val simulertUtbetaling = utbetalingForSimulering.toSimulertUtbetaling(simulering)

    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)

    private val førsteUtbetalingslinjeId = UUID30.randomUUID()
    private val førsteUtbetalingslinje = Utbetalingslinje.Ny(
        id = førsteUtbetalingslinjeId,
        opprettet = Tidspunkt.EPOCH,
        fraOgMed = 1.januar(2020),
        tilOgMed = 31.januar(2020),
        forrigeUtbetalingslinjeId = null,
        beløp = 5,
    )
    private val sak: Sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        utbetalinger = listOf(
            oversendtUtbetaling.copy(
                type = Utbetaling.UtbetalingsType.NY,
                utbetalingslinjer = nonEmptyListOf(
                    førsteUtbetalingslinje,
                ),
            ),
        ),
    )

    private fun expectedUtbetalingslinje(opprettet: Tidspunkt) = Utbetalingslinje.Endring.Stans(
        id = førsteUtbetalingslinje.id,
        opprettet = opprettet,
        fraOgMed = førsteUtbetalingslinje.fraOgMed,
        tilOgMed = førsteUtbetalingslinje.tilOgMed,
        forrigeUtbetalingslinjeId = førsteUtbetalingslinje.forrigeUtbetalingslinjeId,
        beløp = førsteUtbetalingslinje.beløp,
        virkningstidspunkt = 1.januar(2020),
    )

    private val fixedClock: Clock = Clock.fixed(1.januar(2020).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `stans utbetalinger`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()

        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on {
                publish(
                    any(),
                )
            } doReturn oppdragsmelding.right()
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,

        ).stansUtbetalinger(sak.id, saksbehandler, 1.januar(2020))

        response shouldBe sak.right()
        inOrder(
            sakServiceMock,
            simuleringClientMock,
            utbetalingPublisherMock,
            utbetalingRepoMock,
        ) {
            verify(sakServiceMock).hentSak(
                sakId = argThat { it shouldBe sak.id },
            )
            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            expectedUtbetalingslinje(it.utbetalingslinjer[0].opprettet),
                        ),
                    )
                },
            )
            verify(utbetalingPublisherMock).publish(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            expectedUtbetalingslinje(it.utbetalingslinjer[0].opprettet),
                        ),
                    ).toSimulertUtbetaling(simulering)
                },
            )

            verify(utbetalingRepoMock).opprettUtbetaling(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            expectedUtbetalingslinje(it.utbetalingslinjer[0].opprettet),
                        ),
                    ).toSimulertUtbetaling(simulering).toOversendtUtbetaling(oppdragsmelding)
                },
            )
            verify(sakServiceMock).hentSak(
                sakId = argThat { it shouldBe sak.id },
            )
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock,
        )
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når simulering feiler`() {

        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).stansUtbetalinger(sak.id, saksbehandler, 1.januar(2020))

        response shouldBe KunneIkkeStanseUtbetalinger.SimuleringAvStansFeilet.left()

        inOrder(sakServiceMock, simuleringClientMock) {
            verify(sakServiceMock).hentSak(sakId = argThat { it shouldBe sak.id })

            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            expectedUtbetalingslinje(it.utbetalingslinjer[0].opprettet),
                        ),
                    )
                },
            )
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock,
        )
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når utbetaling feiler`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()

        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on {
                publish(any())
            } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(oppdragsmelding).left()
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,

        ).stansUtbetalinger(sak.id, saksbehandler, 1.januar(2020))

        response shouldBe KunneIkkeStanseUtbetalinger.SendingAvUtebetalingTilOppdragFeilet.left()

        inOrder(
            sakServiceMock,
            simuleringClientMock,
            utbetalingPublisherMock,
            utbetalingRepoMock,
        ) {
            verify(sakServiceMock).hentSak(
                sakId = argThat { it shouldBe sak.id },
            )

            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            expectedUtbetalingslinje(it.utbetalingslinjer[0].opprettet),
                        ),
                    )
                },
            )
            verify(utbetalingPublisherMock).publish(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            expectedUtbetalingslinje(it.utbetalingslinjer[0].opprettet),
                        ),
                    ).toSimulertUtbetaling(simulering)
                },
            )
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock,
        )
    }

    @Test
    fun `svarer med feil dersom kontroll av simulering ikke går bra`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 15000,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = førsteUtbetalingslinje.tilOgMed,
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = "",
                                utbetalesTilId = FnrGenerator.random(),
                                utbetalesTilNavn = "",
                                forfall = 1.januar(2020),
                                feilkonto = false,
                                detaljer = listOf(
                                    // Tilsvarer en feilutbetaling
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.januar(2021),
                                        faktiskTilOgMed = 31.januar(2021),
                                        konto = "konto",
                                        belop = 8946,
                                        tilbakeforing = false,
                                        sats = 0,
                                        typeSats = "",
                                        antallSats = 0,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.SUUFORE,
                                        klassekodeBeskrivelse = "",
                                        klasseType = KlasseType.YTEL,
                                    ),
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.januar(2021),
                                        faktiskTilOgMed = 31.januar(2021),
                                        konto = "konto",
                                        belop = 8949,
                                        tilbakeforing = false,
                                        sats = 0,
                                        typeSats = "",
                                        antallSats = 0,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.KL_KODE_FEIL_INNT,
                                        klassekodeBeskrivelse = "",
                                        klasseType = KlasseType.FEIL,
                                    ),
                                    SimulertDetaljer(
                                        faktiskFraOgMed = 1.januar(2021),
                                        faktiskTilOgMed = 31.januar(2021),
                                        konto = "konto",
                                        belop = -8949,
                                        tilbakeforing = true,
                                        sats = 0,
                                        typeSats = "",
                                        antallSats = 0,
                                        uforegrad = 0,
                                        klassekode = KlasseKode.SUUFORE,
                                        klassekodeBeskrivelse = "",
                                        klasseType = KlasseType.YTEL,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ).right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()

        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).stansUtbetalinger(
            sakId = sak.id,
            saksbehandler = saksbehandler,
            stansDato = 1.januar(2020),
        ) shouldBe KunneIkkeStanseUtbetalinger.KontrollAvSimuleringFeilet.left()

        verifyNoMoreInteractions(utbetalingRepoMock, utbetalingPublisherMock)
    }
}
