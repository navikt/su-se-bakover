package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class UtbetalingServiceImplTest {

    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val fnr = Fnr("12345678910")
    private val sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = fixedTidspunkt,
        fnr = fnr,
        utbetalinger = emptyList(),
    )

    private val beregning: Beregning = TestBeregning

    private val attestant = NavIdentBruker.Attestant("SU")

    private val avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt)

    private val oppdragsmelding = Utbetalingsrequest("")

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = idag(fixedClock),
        nettoBeløp = 5155,
        periodeList = listOf(),
    )

    private val listeMedUføregrunnlag = listOf(
        Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = beregning.periode,
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 0,
        ),
    )

    private val dummyUtbetalingslinjer = nonEmptyListOf(
        Utbetalingslinje.Ny(
            opprettet = fixedTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.januar(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 0,
            uføregrad = Uføregrad.parse(50),
        ),
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        id = UUID30.randomUUID(),
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = dummyUtbetalingslinjer,
        type = Utbetaling.UtbetalingsType.NY,
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel,
    )

    private val kvitteringOK = Kvittering(
        utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
        originalKvittering = "",
        mottattTidspunkt = fixedTidspunkt,
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

        verify(utbetalingRepoMock).hentUtbetaling(any<UUID30>())
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock,
        )
    }

    @Test
    fun `hent utbetaling - funnet`() {
        val utbetalingMedKvittering = Utbetaling.OversendtUtbetaling.MedKvittering(
            opprettet = fixedTidspunkt,
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = dummyUtbetalingslinjer,
            fnr = Fnr("12345678910"),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt),
            simulering = simulering,
            utbetalingsrequest = Utbetalingsrequest(""),
            kvittering = kvitteringOK,
        )
        val utbetalingRepoMock =
            mock<UtbetalingRepo> { on { hentUtbetaling(any<UUID30>()) } doReturn utbetalingMedKvittering }
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
            utbetalingPublisherMock,
        )
    }

    @Test
    fun `oppdater med kvittering - ikke funnet`() {

        val avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt)

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
            kvittering = kvitteringOK,
        ) shouldBe FantIkkeUtbetaling.left()

        inOrder(utbetalingRepoMock) {
            verify(utbetalingRepoMock).hentUtbetaling(avstemmingsnøkkel)
            verify(utbetalingRepoMock, Times(0)).oppdaterMedKvittering(any())
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock,
        )
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer ikke fra før`() {
        val utbetalingUtenKvittering = Utbetaling.OversendtUtbetaling.UtenKvittering(
            opprettet = fixedTidspunkt,
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = dummyUtbetalingslinjer,
            fnr = Fnr("12345678910"),
            utbetalingsrequest = Utbetalingsrequest(""),
            simulering = Simulering(
                gjelderId = Fnr("12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
        )
        val kvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
            originalKvittering = "",
            mottattTidspunkt = fixedTidspunkt,
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
            kvittering,
        ) shouldBe postUpdate.right()

        inOrder(utbetalingRepoMock) {
            verify(utbetalingRepoMock).hentUtbetaling(argThat<Avstemmingsnøkkel> { it shouldBe utbetalingUtenKvittering.avstemmingsnøkkel })
            verify(utbetalingRepoMock).oppdaterMedKvittering(argThat { it shouldBe utbetalingMedKvittering })
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock,
        )
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer fra før`() {
        val avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt)
        val utbetaling = Utbetaling.OversendtUtbetaling.MedKvittering(
            opprettet = fixedTidspunkt,
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = dummyUtbetalingslinjer,
            fnr = Fnr("12345678910"),
            utbetalingsrequest = Utbetalingsrequest(""),
            simulering = Simulering(
                gjelderId = Fnr("12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = "",
                mottattTidspunkt = fixedTidspunkt,
            ),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
        )

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(any<Avstemmingsnøkkel>()) } doReturn utbetaling
        }

        val nyKvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
            originalKvittering = "",
            mottattTidspunkt = fixedTidspunkt,
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
            nyKvittering,
        ) shouldBe utbetaling.right()

        inOrder(utbetalingRepoMock) {
            verify(utbetalingRepoMock).hentUtbetaling(avstemmingsnøkkel)
            verify(utbetalingRepoMock, Times(0)).oppdaterMedKvittering(utbetaling)
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock,
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
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
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
            request = UtbetalRequest.NyUtbetaling(
                request = SimulerUtbetalingRequest.NyUtbetaling(
                    sakId = sakId,
                    saksbehandler = attestant,
                    beregning = beregning,
                    uføregrunnlag = listeMedUføregrunnlag,
                ),
                simulering = simulering,
            ),
        ).getOrFail()

        actual shouldBe utbetalingForSimulering.copy(
            id = actual.id,
            opprettet = actual.opprettet,
            avstemmingsnøkkel = actual.avstemmingsnøkkel,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = actual.utbetalingslinjer[0].id,
                    opprettet = actual.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 8637,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        ).toSimulertUtbetaling(simulering).toOversendtUtbetaling(oppdragsmelding)

        verify(sakServiceMock).hentSak(sakId)

        verify(simuleringClientMock).simulerUtbetaling(
            argThat {
                it shouldBe utbetalingForSimulering.copy(
                    id = it.id,
                    opprettet = it.opprettet,
                    avstemmingsnøkkel = it.avstemmingsnøkkel,
                    utbetalingslinjer = nonEmptyListOf(
                        Utbetalingslinje.Ny(
                            id = it.utbetalingslinjer[0].id,
                            opprettet = it.utbetalingslinjer[0].opprettet,
                            fraOgMed = 1.januar(2020),
                            tilOgMed = 31.januar(2020),
                            forrigeUtbetalingslinjeId = null,
                            beløp = 8637,
                            uføregrad = Uføregrad.parse(50),
                        ),
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
                        Utbetalingslinje.Ny(
                            id = it.utbetalingslinjer[0].id,
                            opprettet = it.utbetalingslinjer[0].opprettet,
                            fraOgMed = 1.januar(2020),
                            tilOgMed = 31.januar(2020),
                            forrigeUtbetalingslinjeId = null,
                            beløp = 8637,
                            uføregrad = Uføregrad.parse(50),
                        ),
                    ),
                ).toSimulertUtbetaling(simulering)
            },
        )
        verify(utbetalingRepoMock).defaultTransactionContext()
        verify(utbetalingRepoMock).opprettUtbetaling(
            argThat {
                it shouldBe utbetalingForSimulering.copy(
                    id = it.id,
                    opprettet = it.opprettet,
                    avstemmingsnøkkel = it.avstemmingsnøkkel,
                    utbetalingslinjer = nonEmptyListOf(
                        Utbetalingslinje.Ny(
                            id = it.utbetalingslinjer[0].id,
                            opprettet = it.utbetalingslinjer[0].opprettet,
                            fraOgMed = 1.januar(2020),
                            tilOgMed = 31.januar(2020),
                            forrigeUtbetalingslinjeId = null,
                            beløp = 8637,
                            uføregrad = Uføregrad.parse(50),
                        ),
                    ),
                ).toSimulertUtbetaling(simulering).toOversendtUtbetaling(oppdragsmelding)
            },
            anyOrNull(),
        )
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock,
        )
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
            request = UtbetalRequest.NyUtbetaling(
                request = SimulerUtbetalingRequest.NyUtbetaling(
                    sakId = sakId,
                    saksbehandler = attestant,
                    beregning = beregning,
                    uføregrunnlag = listeMedUføregrunnlag,
                ),
                simulering = simulering,
            ),
        )

        response shouldBe UtbetalingFeilet.Protokollfeil.left()

        inOrder(sakServiceMock, simuleringClientMock, utbetalingPublisherMock) {
            verify(sakServiceMock).hentSak(sakId)
            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            Utbetalingslinje.Ny(
                                id = it.utbetalingslinjer[0].id,
                                opprettet = it.utbetalingslinjer[0].opprettet,
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 8637,
                                uføregrad = Uføregrad.parse(50),
                            ),
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
                            Utbetalingslinje.Ny(
                                id = it.utbetalingslinjer[0].id,
                                opprettet = it.utbetalingslinjer[0].opprettet,
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 8637,
                                uføregrad = Uføregrad.parse(50),
                            ),
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
    fun `returnerer feil dersom kontrollsimulering er ulik innsendt simulering`() {

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.copy(gjelderNavn = "Anne T. Navn").right()
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
            request = UtbetalRequest.NyUtbetaling(
                request = SimulerUtbetalingRequest.NyUtbetaling(
                    sakId = sakId,
                    saksbehandler = attestant,
                    beregning = beregning,
                    uføregrunnlag = listeMedUføregrunnlag,
                ),
                simulering = simulering,
            ),
        )

        actual shouldBe UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
        inOrder(
            sakServiceMock,
            simuleringClientMock,
            utbetalingPublisherMock,
            utbetalingRepoMock,
        ) {
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe sakId })
            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe utbetalingForSimulering.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            Utbetalingslinje.Ny(
                                id = it.utbetalingslinjer[0].id,
                                opprettet = it.utbetalingslinjer[0].opprettet,
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 8637,
                                uføregrad = Uføregrad.parse(50),
                            ),
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

    @Nested
    inner class hentGjeldendeUtbetaling {
        @Test
        fun `henter utbetalingslinjen som gjelder for datoen som sendes inn`() {
            val expectedGjeldendeUtbetalingslinje =
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 53821,
                    uføregrad = Uføregrad.parse(50),
                )

            val utbetalingRepoMock = mock<UtbetalingRepo> {
                on { hentUtbetalinger(any()) } doReturn listOf(
                    utbetalingForSimulering.copy(
                        utbetalingslinjer = nonEmptyListOf(
                            expectedGjeldendeUtbetalingslinje,
                            Utbetalingslinje.Ny(
                                id = UUID30.randomUUID(),
                                opprettet = fixedTidspunkt,
                                fraOgMed = 1.februar(2020),
                                tilOgMed = 29.februar(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 53821,
                                uføregrad = Uføregrad.parse(50),
                            ),
                            Utbetalingslinje.Ny(
                                id = UUID30.randomUUID(),
                                opprettet = fixedTidspunkt,
                                fraOgMed = 1.mars(2020),
                                tilOgMed = 31.mars(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 53821,
                                uføregrad = Uføregrad.parse(50),
                            ),
                        ),
                    ),
                )
            }

            val service = UtbetalingServiceImpl(
                utbetalingRepo = utbetalingRepoMock,
                utbetalingPublisher = mock(),
                sakService = mock(),
                simuleringClient = mock(),
                clock = fixedClock,
            )

            val actual = service.hentGjeldendeUtbetaling(
                sakId = UUID.randomUUID(),
                forDato = 15.januar(2020),
            )

            actual shouldBe UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = expectedGjeldendeUtbetalingslinje.id,
                opprettet = expectedGjeldendeUtbetalingslinje.opprettet,
                periode = Periode.create(
                    expectedGjeldendeUtbetalingslinje.fraOgMed,
                    expectedGjeldendeUtbetalingslinje.tilOgMed,
                ),
                beløp = expectedGjeldendeUtbetalingslinje.beløp,
            ).right()
        }
    }
}
