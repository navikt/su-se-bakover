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
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class UtbetalingServiceImplTest {

    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val fnr = Fnr("12345678910")
    private val sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalinger = emptyList()
    )

    private val beregning: Beregning = TestBeregning

    private val attestant = NavIdentBruker.Attestant("SU")

    private val avstemmingsnøkkel = Avstemmingsnøkkel()

    private val oppdragsmelding = Utbetalingsrequest("")

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = idag(),
        nettoBeløp = 5155,
        periodeList = listOf()
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = listOf(),
        type = Utbetaling.UtbetalingsType.NY,
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel
    )

    val kvitteringOK = Kvittering(
        Kvittering.Utbetalingsstatus.OK,
        ""
    )

    @Test
    fun `hent utbetaling - ikke funnet`() {
        val utbetalingRepoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any<UUID30>()) } doReturn null }
        val sakServiceMock = mock<SakService>()
        val simuleringClientMock = mock<SimuleringClient>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()
        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).hentUtbetaling(UUID30.randomUUID()) shouldBe FantIkkeUtbetaling.left()

        verify(utbetalingRepoMock, Times(1)).hentUtbetaling(any<UUID30>())
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock
        )
    }

    @Test
    fun `hent utbetaling - funnet`() {
        val utbetalingMedKvittering = Utbetaling.OversendtUtbetaling.MedKvittering(
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(),
            simulering = simulering,
            utbetalingsrequest = Utbetalingsrequest(""),
            kvittering = kvitteringOK,
        )
        val utbetalingRepoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any<UUID30>()) } doReturn utbetalingMedKvittering }
        val sakServiceMock = mock<SakService>()
        val simuleringClientMock = mock<SimuleringClient>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()
        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).hentUtbetaling(utbetalingMedKvittering.id) shouldBe utbetalingMedKvittering.right()

        verify(utbetalingRepoMock).hentUtbetaling(utbetalingMedKvittering.id)
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock
        )
    }

    @Test
    fun `oppdater med kvittering - ikke funnet`() {

        val avstemmingsnøkkel = Avstemmingsnøkkel()

        val utbetalingRepoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any<Avstemmingsnøkkel>()) } doReturn null }
        val sakServiceMock = mock<SakService>()
        val simuleringClientMock = mock<SimuleringClient>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()
        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).oppdaterMedKvittering(
            avstemmingsnøkkel = avstemmingsnøkkel,
            kvittering = kvitteringOK
        ) shouldBe FantIkkeUtbetaling.left()

        inOrder(utbetalingRepoMock) {
            verify(utbetalingRepoMock, Times(1)).hentUtbetaling(avstemmingsnøkkel)
            verify(utbetalingRepoMock, Times(0)).oppdaterMedKvittering(any())
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock
        )
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer ikke fra før`() {
        val utbetalingUtenKvittering = Utbetaling.OversendtUtbetaling.UtenKvittering(
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            utbetalingsrequest = Utbetalingsrequest(""),
            simulering = Simulering(
                gjelderId = Fnr("12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )
        val kvittering = Kvittering(
            Kvittering.Utbetalingsstatus.OK,
            ""
        )
        val utbetalingMedKvittering = utbetalingUtenKvittering.toKvittertUtbetaling(kvittering)

        val postUpdate = utbetalingUtenKvittering.toKvittertUtbetaling(kvittering)

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(any<Avstemmingsnøkkel>()) } doReturn utbetalingUtenKvittering
        }

        val sakServiceMock = mock<SakService>()
        val simuleringClientMock = mock<SimuleringClient>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).oppdaterMedKvittering(
            utbetalingUtenKvittering.avstemmingsnøkkel,
            kvittering
        ) shouldBe postUpdate.right()

        inOrder(utbetalingRepoMock) {
            verify(utbetalingRepoMock).hentUtbetaling(argThat<Avstemmingsnøkkel> { it shouldBe utbetalingUtenKvittering.avstemmingsnøkkel })
            verify(utbetalingRepoMock).oppdaterMedKvittering(argThat { it shouldBe utbetalingMedKvittering })
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock
        )
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer fra før`() {
        val avstemmingsnøkkel = Avstemmingsnøkkel()
        val utbetaling = Utbetaling.OversendtUtbetaling.MedKvittering(
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            utbetalingsrequest = Utbetalingsrequest(""),
            simulering = Simulering(
                gjelderId = Fnr("12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            type = Utbetaling.UtbetalingsType.NY,
            kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = ""
            ),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(any<Avstemmingsnøkkel>()) } doReturn utbetaling
        }

        val nyKvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
            originalKvittering = ""
        )

        val sakServiceMock = mock<SakService>()
        val simuleringClientMock = mock<SimuleringClient>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()
        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).oppdaterMedKvittering(
            avstemmingsnøkkel,
            nyKvittering
        ) shouldBe utbetaling.right()

        inOrder(utbetalingRepoMock) {
            verify(utbetalingRepoMock, Times(1)).hentUtbetaling(avstemmingsnøkkel)
            verify(utbetalingRepoMock, Times(0)).oppdaterMedKvittering(utbetaling)
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock
        )
    }

    @Test
    fun `utbetaler penger og lagrer utbetaling`() {

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { opprettUtbetaling(any()) }.doNothing()
        }
        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on { publish(any()) } doReturn oppdragsmelding.right()
        }

        val actual = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            clock = fixedClock,
        ).utbetal(
            sakId = sakId,
            attestant = attestant,
            beregning = beregning,
            simulering = simulering
        ).orNull()!!

        actual shouldBe utbetalingForSimulering.copy(
            id = actual.id,
            opprettet = actual.opprettet,
            avstemmingsnøkkel = actual.avstemmingsnøkkel,
            utbetalingslinjer = listOf(
                Utbetalingslinje.Ny(
                    id = actual.utbetalingslinjer[0].id,
                    opprettet = actual.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 8637
                )
            )
        ).toSimulertUtbetaling(simulering).toOversendtUtbetaling(oppdragsmelding)

        inOrder(
            sakServiceMock,
            simuleringClientMock,
            utbetalingPublisherMock,
            utbetalingRepoMock
        ) {
            verify(sakServiceMock).hentSak(sakId)

            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = listOf(
                            Utbetalingslinje.Ny(
                                id = it.utbetalingslinjer[0].id,
                                opprettet = it.utbetalingslinjer[0].opprettet,
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 8637
                            )
                        )
                    )
                }
            )
            verify(utbetalingPublisherMock).publish(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = listOf(
                            Utbetalingslinje.Ny(
                                id = it.utbetalingslinjer[0].id,
                                opprettet = it.utbetalingslinjer[0].opprettet,
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 8637
                            )
                        )
                    ).toSimulertUtbetaling(simulering)
                }
            )
            verify(utbetalingRepoMock).opprettUtbetaling(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = listOf(
                            Utbetalingslinje.Ny(
                                id = it.utbetalingslinjer[0].id,
                                opprettet = it.utbetalingslinjer[0].opprettet,
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 8637
                            )
                        )
                    ).toSimulertUtbetaling(simulering).toOversendtUtbetaling(oppdragsmelding)
                }
            )
            verifyNoMoreInteractions(
                sakServiceMock,
                simuleringClientMock,
                utbetalingRepoMock,
                utbetalingPublisherMock
            )
        }
    }

    @Test
    fun `returnerer feilmelding dersom utbetaling feiler`() {

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on { publish(any()) } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(oppdragsmelding).left()
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            clock = fixedClock,
        ).utbetal(
            sakId = sakId,
            attestant = attestant,
            beregning = beregning,
            simulering = simulering
        )

        response shouldBe KunneIkkeUtbetale.Protokollfeil.left()

        inOrder(sakServiceMock, simuleringClientMock, utbetalingPublisherMock) {
            verify(sakServiceMock).hentSak(sakId)
            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = listOf(
                            Utbetalingslinje.Ny(
                                id = it.utbetalingslinjer[0].id,
                                opprettet = it.utbetalingslinjer[0].opprettet,
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 8637
                            )
                        )
                    )
                }
            )
            verify(utbetalingPublisherMock).publish(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = listOf(
                            Utbetalingslinje.Ny(
                                id = it.utbetalingslinjer[0].id,
                                opprettet = it.utbetalingslinjer[0].opprettet,
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 8637
                            )
                        )
                    ).toSimulertUtbetaling(simulering)
                }
            )
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock
        )
    }

    @Test
    fun `returnerer feil dersom kontrollsimulering er ulik innsendt simulering`() {

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.copy(nettoBeløp = 1234).right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        val actual = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            clock = fixedClock,
        ).utbetal(
            sakId = sakId,
            attestant = attestant,
            beregning = beregning,
            simulering = simulering
        )

        actual shouldBe KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
        inOrder(
            sakServiceMock,
            simuleringClientMock,
            utbetalingPublisherMock,
            utbetalingRepoMock
        ) {
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = listOf(
                            Utbetalingslinje.Ny(
                                id = it.utbetalingslinjer[0].id,
                                opprettet = it.utbetalingslinjer[0].opprettet,
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 8637
                            )
                        )
                    )
                }
            )
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock
        )
    }
}
