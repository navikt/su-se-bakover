package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class GjenopptaUtbetalingerServiceTest {

    @Test
    fun `Utbetalinger som er stanset blir startet igjen`() {

        val oppdragMock = mock<Oppdrag> {
            on { genererUtbetaling(any(), any()) } doReturn utbetalingForSimulering
        }
        val sak = sak.copy(oppdrag = oppdragMock)

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { opprettUtbetaling(any()) }.doNothing()
        }

        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on {
                publish(
                    any()
                )
            } doReturn oppdragsmelding.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock
        ).gjenopptaUtbetalinger(sak.id, saksbehandler)

        response shouldBe sak.right()
        inOrder(
            sakServiceMock,
            oppdragMock,
            simuleringClientMock,
            utbetalingPublisherMock,
            utbetalingRepoMock
        ) {
            verify(sakServiceMock).hentSak(
                sakId = argThat { it shouldBe sak.id }
            )
            verify(oppdragMock).genererUtbetaling(
                strategy = argThat { it shouldBe Oppdrag.UtbetalingStrategy.Gjenoppta(saksbehandler) },
                fnr = argThat { it shouldBe fnr }
            )
            verify(simuleringClientMock).simulerUtbetaling(
                argThat { it shouldBe utbetalingForSimulering }
            )
            verify(utbetalingPublisherMock).publish(
                argThat { it shouldBe simulertUtbetaling }
            )

            verify(utbetalingRepoMock).opprettUtbetaling(
                argThat {
                    it shouldBe oversendtUtbetaling
                }
            )
            verify(sakServiceMock).hentSak(
                sakId = argThat { it shouldBe sak.id }
            )
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            oppdragMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock
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
            simuleringClient = simuleringClientMock
        )

        val response = service.gjenopptaUtbetalinger(sak.id, saksbehandler)
        response shouldBe KunneIkkeGjenopptaUtbetalinger.FantIkkeSak.left()

        verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verifyNoMoreInteractions(sakServiceMock, utbetalingRepoMock, utbetalingPublisherMock, simuleringClientMock)
    }

    @Test
    fun `Simulering feiler`() {

        val oppdragMock = mock<Oppdrag> {
            on { genererUtbetaling(any(), any()) } doReturn utbetalingForSimulering
        }
        val sak = sak.copy(oppdrag = oppdragMock)

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()
        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            clock = fixedClock
        ).gjenopptaUtbetalinger(sak.id, saksbehandler)

        response shouldBe KunneIkkeGjenopptaUtbetalinger.SimuleringAvStartutbetalingFeilet.left()

        inOrder(sakServiceMock, oppdragMock, simuleringClientMock) {
            verify(sakServiceMock).hentSak(sakId = argThat { it shouldBe sak.id })
            verify(oppdragMock).genererUtbetaling(
                strategy = argThat { it shouldBe Oppdrag.UtbetalingStrategy.Gjenoppta(saksbehandler) },
                fnr = argThat { it shouldBe fnr }
            )
            verify(simuleringClientMock).simulerUtbetaling(argThat { it shouldBe utbetalingForSimulering })
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            oppdragMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock
        )
    }

    @Test
    fun `Utbetaling feilet`() {
        val oppdragMock = mock<Oppdrag> {
            on { genererUtbetaling(any(), any()) } doReturn utbetalingForSimulering
        }
        val sak = sak.copy(oppdrag = oppdragMock)

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
            simuleringClient = simuleringClientMock
        ).gjenopptaUtbetalinger(sak.id, saksbehandler)

        response shouldBe KunneIkkeGjenopptaUtbetalinger.SendingAvUtebetalingTilOppdragFeilet.left()

        inOrder(sakServiceMock, oppdragMock, simuleringClientMock, utbetalingPublisherMock) {
            verify(sakServiceMock).hentSak(sakId = argThat { sak.id })
            verify(oppdragMock).genererUtbetaling(
                strategy = argThat { it shouldBe Oppdrag.UtbetalingStrategy.Gjenoppta(saksbehandler) },
                fnr = argThat { it shouldBe fnr }
            )
            verify(simuleringClientMock).simulerUtbetaling(argThat { utbetalingForSimulering })
            verify(utbetalingPublisherMock).publish(argThat { simulertUtbetaling })
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            oppdragMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingRepoMock
        )
    }

    private val tidspunkt = 15.juni(2020).startOfDay()
    private val fixedClock = Clock.fixed(tidspunkt.instant, ZoneOffset.UTC)
    private val fnr = Fnr("12345678910")
    private val sakId = UUID.randomUUID()
    private val saksbehandler = NavIdentBruker.Saksbehandler("AB12345")
    private val avstemmingsnøkkel = Avstemmingsnøkkel(tidspunkt)

    private val oppdrag: Oppdrag = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = tidspunkt,
        sakId = sakId,
        utbetalinger = emptyList()
    )

    private val sak: Sak = Sak(
        id = sakId,
        opprettet = tidspunkt,
        fnr = fnr,
        oppdrag = oppdrag
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        id = UUID30.randomUUID(),
        opprettet = tidspunkt,
        fnr = fnr,
        utbetalingslinjer = listOf(),
        type = Utbetaling.UtbetalingsType.GJENOPPTA,
        oppdragId = oppdrag.id,
        behandler = saksbehandler,
        avstemmingsnøkkel = avstemmingsnøkkel
    )

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = idag(),
        nettoBeløp = 13.0,
        periodeList = listOf()
    )
    private val oppdragsmelding = Utbetalingsrequest(
        value = ""
    )
    private val simulertUtbetaling = utbetalingForSimulering.toSimulertUtbetaling(simulering)
    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)
}
