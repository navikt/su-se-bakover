package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
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
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GjenopptaUtbetalingerServiceTest {

    private val tidspunkt = 15.juni(2020).startOfDay()
    private val fnr = Fnr("12345678910")
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val saksbehandler = NavIdentBruker.Saksbehandler("AB12345")
    private val avstemmingsnøkkel = Avstemmingsnøkkel(tidspunkt)

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = idag(),
        nettoBeløp = 13,
        periodeList = listOf(),
    )

    private val oppdragsmelding = Utbetalingsrequest("<xml></xml>")

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        id = UUID30.randomUUID(),
        opprettet = tidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = nonEmptyListOf(
            Utbetalingslinje.Ny(
                opprettet = Tidspunkt.EPOCH,
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.januar(2020),
                forrigeUtbetalingslinjeId = null,
                beløp = 0,
            ),
        ),
        type = Utbetaling.UtbetalingsType.GJENOPPTA,
        behandler = saksbehandler,
        avstemmingsnøkkel = avstemmingsnøkkel,
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
        opprettet = tidspunkt,
        fnr = fnr,
        utbetalinger = listOf(
            oversendtUtbetaling.copy(
                type = Utbetaling.UtbetalingsType.NY,
                utbetalingslinjer = nonEmptyListOf(
                    førsteUtbetalingslinje,
                ),
            ),
            oversendtUtbetaling.copy(
                type = Utbetaling.UtbetalingsType.STANS,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Stans(
                        utbetalingslinje = førsteUtbetalingslinje,
                        virkningstidspunkt = 1.januar(2020),
                    ),
                ),
            ),
        ),
    )

    private fun expected(opprettet: Tidspunkt) = Utbetalingslinje.Endring.Reaktivering(
        id = førsteUtbetalingslinje.id,
        opprettet = opprettet,
        fraOgMed = førsteUtbetalingslinje.fraOgMed,
        tilOgMed = førsteUtbetalingslinje.tilOgMed,
        forrigeUtbetalingslinjeId = førsteUtbetalingslinje.forrigeUtbetalingslinjeId,
        beløp = førsteUtbetalingslinje.beløp,
        virkningstidspunkt = 1.januar(2020),
    )

    @Test
    fun `Utbetalinger som er stanset blir startet igjen`() {

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { opprettUtbetaling(any()) }.doNothing()
        }

        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on {
                publish(
                    any(),
                )
            } doReturn oppdragsmelding.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val actual = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            clock = fixedClock,
        ).gjenopptaUtbetalinger(sak.id, saksbehandler)

        actual shouldBe sak.right()
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
                            expected(it.utbetalingslinjer[0].opprettet),
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
                            expected(it.utbetalingslinjer[0].opprettet),
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
                            expected(it.utbetalingslinjer[0].opprettet),
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

        val response = service.gjenopptaUtbetalinger(sak.id, saksbehandler)
        response shouldBe KunneIkkeGjenopptaUtbetalinger.FantIkkeSak.left()

        verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verifyNoMoreInteractions(sakServiceMock, utbetalingRepoMock, utbetalingPublisherMock, simuleringClientMock)
    }

    @Test
    fun `Simulering feiler`() {

        val sak = sak

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()
        val actual = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            clock = fixedClock,
        ).gjenopptaUtbetalinger(sak.id, saksbehandler)

        actual shouldBe KunneIkkeGjenopptaUtbetalinger.SimuleringAvStartutbetalingFeilet.left()

        inOrder(sakServiceMock, simuleringClientMock) {
            verify(sakServiceMock).hentSak(sakId = argThat { it shouldBe sak.id })

            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            expected(it.utbetalingslinjer[0].opprettet),
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
    fun `Utbetaling feilet`() {
        val sak = sak

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
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
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            clock = fixedClock,
        ).gjenopptaUtbetalinger(sak.id, saksbehandler)

        response shouldBe KunneIkkeGjenopptaUtbetalinger.SendingAvUtebetalingTilOppdragFeilet.left()

        inOrder(sakServiceMock, simuleringClientMock, utbetalingPublisherMock) {
            verify(sakServiceMock).hentSak(sakId = argThat { sak.id })

            verify(simuleringClientMock).simulerUtbetaling(argThat { utbetalingForSimulering })
            verify(utbetalingPublisherMock).publish(argThat { simulertUtbetaling })
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingRepoMock,
        )
    }
}
