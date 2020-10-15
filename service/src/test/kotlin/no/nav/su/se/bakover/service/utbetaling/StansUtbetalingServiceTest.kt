package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.SENDT
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.toOversendtUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.toSimulertUtbetaling
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class StansUtbetalingServiceTest {

    @Test
    fun `stans utbetalinger`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                lagUtbetaling(sak.id, strategy)
            } doReturn nyUtbetaling
            on {
                simulerUtbetaling(nyUtbetaling)
            } doReturn simulertUtbetaling.right()
            on {
                utbetal(
                    argThat {
                        it.oppdrag shouldBe oppdrag
                        it.utbetaling shouldBe simulertUtbetaling
                        it.attestant shouldBe attestant
                        it.avstemmingsnøkkel shouldBe avstemmingsnøkkel
                    }
                )
            } doReturn oversendtUtbetaling.right()
        }

        val response = StansUtbetalingService(
            utbetalingService = utbetalingServiceMock,
            sakService = sakServiceMock
        ).stansUtbetalinger(sak.id, saksbehandler)

        response shouldBe sak.right()
        verify(utbetalingServiceMock, Times(1)).lagUtbetaling(sak.id, strategy)
        verify(utbetalingServiceMock, Times(1)).simulerUtbetaling(nyUtbetaling)
        verify(utbetalingServiceMock, Times(1)).utbetal(tilUtbetaling)
        verify(sakServiceMock, Times(2)).hentSak(sak.id)
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når simulering feiler`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                lagUtbetaling(sak.id, strategy)
            } doReturn nyUtbetaling
            on {
                simulerUtbetaling(nyUtbetaling)
            } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }
        val response = StansUtbetalingService(
            utbetalingService = utbetalingServiceMock,
            sakService = sakServiceMock
        ).stansUtbetalinger(sak.id, saksbehandler)

        response shouldBe StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()

        verify(sakServiceMock).hentSak(sak.id)
        verify(utbetalingServiceMock).lagUtbetaling(sak.id, strategy)
        verify(utbetalingServiceMock).simulerUtbetaling(nyUtbetaling)
        verifyNoMoreInteractions(utbetalingServiceMock, sakServiceMock)
    }

    @Test
    fun `svarer med feil dersom simulering inneholder beløp større enn 0`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                lagUtbetaling(sak.id, strategy)
            } doReturn nyUtbetaling
            on {
                simulerUtbetaling(nyUtbetaling)
            } doReturn simulertUtbetaling.copy(
                simulering = simulering.copy(
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
                )
            ).right()
        }
        val response = StansUtbetalingService(
            utbetalingService = utbetalingServiceMock,
            sakService = sakServiceMock
        ).stansUtbetalinger(sak.id, saksbehandler)

        response shouldBe StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()

        verify(sakServiceMock, Times(1)).hentSak(sak.id)
        verify(utbetalingServiceMock).lagUtbetaling(sak.id, strategy)
        verify(utbetalingServiceMock).simulerUtbetaling(nyUtbetaling)
        verifyNoMoreInteractions(utbetalingServiceMock, sakServiceMock)
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når utbetaling feiler`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                lagUtbetaling(sak.id, strategy)
            } doReturn nyUtbetaling
            on {
                simulerUtbetaling(nyUtbetaling)
            } doReturn simulertUtbetaling.right()
            on {
                utbetal(
                    argThat {
                        it.oppdrag shouldBe oppdrag
                        it.utbetaling shouldBe simulertUtbetaling
                        it.attestant shouldBe attestant
                        it.avstemmingsnøkkel shouldBe avstemmingsnøkkel
                    }
                )
            } doReturn UtbetalingFeilet.left()
        }

        val response = StansUtbetalingService(
            utbetalingService = utbetalingServiceMock,
            sakService = sakServiceMock
        ).stansUtbetalinger(sak.id, saksbehandler)

        response shouldBe StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()
        verify(sakServiceMock, Times(1)).hentSak(sak.id)
        verify(utbetalingServiceMock).lagUtbetaling(sak.id, strategy)
        verify(utbetalingServiceMock).simulerUtbetaling(nyUtbetaling)
        verify(utbetalingServiceMock).utbetal(tilUtbetaling)
    }

    private val fnr = Fnr("12345678910")
    private val sakId = UUID.randomUUID()
    private val saksbehandler = Saksbehandler("Z123")
    private val attestant = Attestant("SU")
    private val avstemmingsnøkkel = Avstemmingsnøkkel()
    private val strategy = Oppdrag.UtbetalingStrategy.Stans()
    private val oppdrag: Oppdrag = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = sakId,
        utbetalinger = emptyList()
    )

    private val sak: Sak = Sak(
        id = sakId,
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        oppdrag = oppdrag
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        utbetalingslinjer = listOf(),
        type = Utbetaling.UtbetalingsType.STANS
    )

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = idag(),
        nettoBeløp = 0,
        periodeList = listOf()
    )
    private val oppdragsmelding = Oppdragsmelding(
        status = SENDT,
        originalMelding = "",
        avstemmingsnøkkel = avstemmingsnøkkel
    )
    private val simulertUtbetaling = utbetalingForSimulering.toSimulertUtbetaling(simulering)
    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)
    private val nyUtbetaling = OversendelseTilOppdrag.NyUtbetaling(
        oppdrag = oppdrag,
        utbetaling = utbetalingForSimulering,
        attestant = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel
    )
    private val tilUtbetaling = OversendelseTilOppdrag.TilUtbetaling(
        oppdrag = oppdrag,
        utbetaling = simulertUtbetaling,
        attestant = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel
    )
}
