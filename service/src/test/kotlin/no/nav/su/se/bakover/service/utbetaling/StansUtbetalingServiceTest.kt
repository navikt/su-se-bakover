package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
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
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class StansUtbetalingServiceTest {

    @Test
    fun `stans utbetalinger`() {
        val oppdragMock = mock<Oppdrag> {
            on { genererUtbetaling(any(), any()) } doReturn utbetalingForSimulering
        }

        val sak = sak.copy(oppdrag = oppdragMock)
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
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

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock

        ).stansUtbetalinger(sak.id, saksbehandler)

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
                strategy = argThat { it shouldBe Oppdrag.UtbetalingStrategy.Stans(saksbehandler) },
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
    fun `Sjekk at vi svarer furnuftig når simulering feiler`() {
        val oppdragMock = mock<Oppdrag> {
            on { genererUtbetaling(any(), any()) } doReturn utbetalingForSimulering
        }

        val sak = sak.copy(oppdrag = oppdragMock)
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
        ).stansUtbetalinger(sak.id, saksbehandler)

        response shouldBe KunneIkkeStanseUtbetalinger.SimuleringAvStansFeilet.left()

        inOrder(sakServiceMock, oppdragMock, simuleringClientMock) {
            verify(sakServiceMock).hentSak(sakId = argThat { it shouldBe sak.id })
            verify(oppdragMock).genererUtbetaling(
                strategy = argThat { it shouldBe Oppdrag.UtbetalingStrategy.Stans(saksbehandler) },
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
    fun `svarer med feil dersom simulering inneholder beløp større enn 0`() {
        val oppdragMock = mock<Oppdrag> {
            on { genererUtbetaling(any(), any()) } doReturn utbetalingForSimulering
        }

        val sak = sak.copy(oppdrag = oppdragMock)
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.copy(
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = idag(),
                        tilOgMed = idag(),
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = "",
                                utbetalesTilId = fnr,
                                utbetalesTilNavn = "",
                                forfall = idag(),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimulertDetaljer(
                                        faktiskFraOgMed = idag(),
                                        faktiskTilOgMed = idag(),
                                        konto = "",
                                        belop = 1234,
                                        tilbakeforing = false,
                                        sats = 1234,
                                        typeSats = "",
                                        antallSats = 1,
                                        uforegrad = 0,
                                        klassekode = "",
                                        klassekodeBeskrivelse = "",
                                        klasseType = KlasseType.YTEL
                                    )
                                )
                            )
                        )
                    )
                )
            ).right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
        ).stansUtbetalinger(sak.id, saksbehandler)

        response shouldBe KunneIkkeStanseUtbetalinger.SimulertStansHarBeløpUlikt0.left()

        inOrder(sakServiceMock, oppdragMock, simuleringClientMock) {
            verify(sakServiceMock).hentSak(sakId = argThat { it shouldBe sak.id })
            verify(oppdragMock).genererUtbetaling(
                strategy = argThat { it shouldBe Oppdrag.UtbetalingStrategy.Stans(saksbehandler) },
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
    fun `Sjekk at vi svarer furnuftig når utbetaling feiler`() {
        val oppdragMock = mock<Oppdrag> {
            on { genererUtbetaling(any(), any()) } doReturn utbetalingForSimulering
        }

        val sak = sak.copy(oppdrag = oppdragMock)
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { opprettUtbetaling(any()) }.doNothing()
        }

        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on {
                publish(any())
            } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(oppdragsmelding).left()
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock

        ).stansUtbetalinger(sak.id, saksbehandler)

        response shouldBe KunneIkkeStanseUtbetalinger.SendingAvUtebetalingTilOppdragFeilet.left()

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
                strategy = argThat { it shouldBe Oppdrag.UtbetalingStrategy.Stans(saksbehandler) },
                fnr = argThat { it shouldBe fnr }
            )
            verify(simuleringClientMock).simulerUtbetaling(
                argThat { it shouldBe utbetalingForSimulering }
            )
            verify(utbetalingPublisherMock).publish(
                argThat { it shouldBe simulertUtbetaling }
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

    private val fnr = Fnr("12345678910")
    private val sakId = UUID.randomUUID()
    private val saksbehandler = NavIdentBruker.Saksbehandler("Z123")
    private val avstemmingsnøkkel = Avstemmingsnøkkel()

    private val sak: Sak = Sak(
        id = sakId,
        saksnummer = Saksnummer(Math.random().toInt()),
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        oppdrag = mock()
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        utbetalingslinjer = listOf(),
        type = Utbetaling.UtbetalingsType.STANS,
        oppdragId = UUID30.randomUUID(),
        behandler = NavIdentBruker.Saksbehandler("Z123"),
        avstemmingsnøkkel = avstemmingsnøkkel
    )

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = idag(),
        nettoBeløp = 0,
        periodeList = listOf()
    )
    private val oppdragsmelding = Utbetalingsrequest(
        value = ""
    )
    private val simulertUtbetaling = utbetalingForSimulering.toSimulertUtbetaling(simulering)
    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)
}
