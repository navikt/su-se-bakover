package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
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
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simuleringNy
import no.nav.su.se.bakover.test.simuleringOpphørt
import no.nav.su.se.bakover.test.simuleringStans
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
        UtbetalingServiceAndMocks(
            utbetalingRepo = mock {
                on { hentUtbetaling(any<UUID30>()) } doReturn null
            },
        ).let {
            it.service.hentUtbetaling(UUID30.randomUUID()) shouldBe FantIkkeUtbetaling.left()
        }
    }

    @Test
    fun `oppdater med kvittering - ikke funnet`() {
        UtbetalingServiceAndMocks(
            utbetalingRepo = mock {
                on { hentUtbetaling(any<Avstemmingsnøkkel>()) } doReturn null
            },
        ).let {
            it.service.oppdaterMedKvittering(
                avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(fixedClock)),
                kvittering = kvitteringOK,
            ) shouldBe FantIkkeUtbetaling.left()
        }
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer ikke fra før`() {
        val utbetalingUtenKvittering = vedtakSøknadsbehandlingIverksattInnvilget().let { (sak, _) ->
            (sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering).let {
                Utbetaling.OversendtUtbetaling.UtenKvittering(
                    id = it.id,
                    opprettet = it.opprettet,
                    sakId = it.sakId,
                    saksnummer = it.saksnummer,
                    fnr = it.fnr,
                    utbetalingslinjer = it.utbetalingslinjer,
                    type = it.type,
                    behandler = it.behandler,
                    avstemmingsnøkkel = it.avstemmingsnøkkel,
                    simulering = it.simulering,
                    utbetalingsrequest = it.utbetalingsrequest,
                )
            }
        }
        val utbetalingMedKvittering = utbetalingUtenKvittering.toKvittertUtbetaling(kvitteringOK)

        UtbetalingServiceAndMocks(
            utbetalingRepo = mock {
                on { hentUtbetaling(any<Avstemmingsnøkkel>()) } doReturn utbetalingUtenKvittering
            },
        ).let {
            it.service.oppdaterMedKvittering(
                avstemmingsnøkkel = utbetalingUtenKvittering.avstemmingsnøkkel,
                kvittering = kvitteringOK,
            ).getOrFail() shouldBe utbetalingMedKvittering

            verify(it.utbetalingRepo).oppdaterMedKvittering(utbetalingMedKvittering)
        }
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer fra før`() {
        val utbetalingUtenKvittering = vedtakSøknadsbehandlingIverksattInnvilget().let { (sak, _) ->
            (sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering).let {
                Utbetaling.OversendtUtbetaling.UtenKvittering(
                    id = it.id,
                    opprettet = it.opprettet,
                    sakId = it.sakId,
                    saksnummer = it.saksnummer,
                    fnr = it.fnr,
                    utbetalingslinjer = it.utbetalingslinjer,
                    type = it.type,
                    behandler = it.behandler,
                    avstemmingsnøkkel = it.avstemmingsnøkkel,
                    simulering = it.simulering,
                    utbetalingsrequest = it.utbetalingsrequest,
                )
            }
        }
        val utbetalingMedKvittering = utbetalingUtenKvittering.toKvittertUtbetaling(kvitteringOK)

        UtbetalingServiceAndMocks(
            utbetalingRepo = mock {
                on { hentUtbetaling(any<Avstemmingsnøkkel>()) } doReturn utbetalingMedKvittering
            },
        ).let {
            it.service.oppdaterMedKvittering(
                avstemmingsnøkkel = utbetalingUtenKvittering.avstemmingsnøkkel,
                kvittering = kvitteringOK,
            ).getOrFail() shouldBe utbetalingMedKvittering

            verify(it.utbetalingRepo, never()).oppdaterMedKvittering(any())
        }
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
                it shouldBe SimulerUtbetalingForPeriode(
                    utbetaling = utbetalingForSimulering.copy(
                        id = it.utbetaling.id,
                        opprettet = it.utbetaling.opprettet,
                        avstemmingsnøkkel = it.utbetaling.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            Utbetalingslinje.Ny(
                                id = it.utbetaling.utbetalingslinjer[0].id,
                                opprettet = it.utbetaling.utbetalingslinjer[0].opprettet,
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                                forrigeUtbetalingslinjeId = null,
                                beløp = 8637,
                                uføregrad = Uføregrad.parse(50),
                            ),
                        ),
                    ),
                    simuleringsperiode = Periode.create(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.januar(2020),
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
                    it shouldBe SimulerUtbetalingForPeriode(
                        utbetaling = utbetalingForSimulering.copy(
                            id = it.utbetaling.id,
                            opprettet = it.utbetaling.opprettet,
                            avstemmingsnøkkel = it.utbetaling.avstemmingsnøkkel,
                            utbetalingslinjer = nonEmptyListOf(
                                Utbetalingslinje.Ny(
                                    id = it.utbetaling.utbetalingslinjer[0].id,
                                    opprettet = it.utbetaling.utbetalingslinjer[0].opprettet,
                                    fraOgMed = 1.januar(2020),
                                    tilOgMed = 31.januar(2020),
                                    forrigeUtbetalingslinjeId = null,
                                    beløp = 8637,
                                    uføregrad = Uføregrad.parse(50),
                                ),
                            ),
                        ),
                        simuleringsperiode = Periode.create(
                            fraOgMed = 1.januar(2020),
                            tilOgMed = 31.januar(2020),
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
                    it shouldBe SimulerUtbetalingForPeriode(
                        utbetaling = utbetalingForSimulering.copy(
                            id = it.utbetaling.id,
                            opprettet = it.utbetaling.opprettet,
                            avstemmingsnøkkel = it.utbetaling.avstemmingsnøkkel,
                            utbetalingslinjer = nonEmptyListOf(
                                Utbetalingslinje.Ny(
                                    id = it.utbetaling.utbetalingslinjer[0].id,
                                    opprettet = it.utbetaling.utbetalingslinjer[0].opprettet,
                                    fraOgMed = 1.januar(2020),
                                    tilOgMed = 31.januar(2020),
                                    forrigeUtbetalingslinjeId = null,
                                    beløp = 8637,
                                    uføregrad = Uføregrad.parse(50),
                                ),
                            ),
                        ),
                        simuleringsperiode = Periode.create(
                            fraOgMed = 1.januar(2020),
                            tilOgMed = 31.januar(2020),
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

    @Nested
    inner class SimuleringsperiodeTest {
        @Test
        fun `simuleringsperiode settes til fra virkningstidspunkt til slutt på utbetalingslinje ved stans`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()

            UtbetalingServiceAndMocks(
                sakService = mock {
                    on { hentSak(any<UUID>()) } doReturn sak.right()
                },
                simuleringClient = mock<SimuleringClient>() {
                    on { simulerUtbetaling(any()) } doReturn simuleringStans(
                        stansDato = 1.februar(2021),
                        eksisterendeUtbetalinger = sak.utbetalinger,
                        fnr = sak.fnr,
                        sakId = sak.id,
                        saksnummer = sak.saksnummer,
                        clock = fixedClock,
                    ).right()
                },
                clock = fixedClock,
            ).let {
                it.service.simulerStans(
                    request = SimulerUtbetalingRequest.Stans(
                        sakId = sak.id,
                        saksbehandler = saksbehandler,
                        stansdato = 1.februar(2021),
                    ),
                ).getOrFail() shouldBe beOfType<Utbetaling.SimulertUtbetaling>()

                verify(it.simuleringClient).simulerUtbetaling(
                    request = argThat {
                        it shouldBe beOfType<SimulerUtbetalingForPeriode>()
                        it.utbetaling.type shouldBe Utbetaling.UtbetalingsType.STANS
                        it.simuleringsperiode shouldBe Periode.create(1.februar(2021), 31.desember(2021))
                    },
                )
            }
        }

        @Test
        fun `simuleringsperiode settes til fra virkningstidspunkt til slutt på utbetalingslinje ved opphør`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()

            UtbetalingServiceAndMocks(
                sakService = mock {
                    on { hentSak(any<UUID>()) } doReturn sak.right()
                },
                simuleringClient = mock<SimuleringClient>() {
                    on { simulerUtbetaling(any()) } doReturn simuleringOpphørt(
                        opphørsdato = 1.februar(2021),
                        eksisterendeUtbetalinger = sak.utbetalinger,
                        fnr = sak.fnr,
                        sakId = sak.id,
                        saksnummer = sak.saksnummer,
                        clock = fixedClock,
                    ).right()
                },
                clock = fixedClock,
            ).let {
                it.service.simulerOpphør(
                    request = SimulerUtbetalingRequest.Opphør(
                        sakId = sak.id,
                        saksbehandler = saksbehandler,
                        opphørsdato = 1.februar(2021),
                    ),
                ).getOrFail() shouldBe beOfType<Utbetaling.SimulertUtbetaling>()

                verify(it.simuleringClient).simulerUtbetaling(
                    request = argThat {
                        it shouldBe beOfType<SimulerUtbetalingForPeriode>()
                        it.utbetaling.type shouldBe Utbetaling.UtbetalingsType.OPPHØR
                        it.simuleringsperiode shouldBe Periode.create(1.februar(2021), 31.desember(2021))
                    },
                )
            }
        }

        @Test
        fun `simuleringsperiode settes til fra virkningstidspunkt til slutt på utbetalingslinje ved reaktivering`() {
            val (sak, stans) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak()

            UtbetalingServiceAndMocks(
                sakService = mock {
                    on { hentSak(any<UUID>()) } doReturn sak.right()
                },
                simuleringClient = mock<SimuleringClient>() {
                    on { simulerUtbetaling(any()) } doReturn simuleringNy(
                        beregning = (stans.behandling.tilRevurdering as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling).beregning,
                        eksisterendeUtbetalinger = sak.utbetalinger,
                        fnr = sak.fnr,
                        sakId = sak.id,
                        saksnummer = sak.saksnummer,
                        clock = fixedClock,
                    ).right()
                },
            ).let {
                it.service.simulerGjenopptak(
                    request = SimulerUtbetalingRequest.Gjenopptak(
                        saksbehandler = saksbehandler,
                        sak = sak,
                    ),
                ).getOrFail() shouldBe beOfType<Utbetaling.SimulertUtbetaling>()

                verify(it.simuleringClient).simulerUtbetaling(
                    request = argThat {
                        it shouldBe beOfType<SimulerUtbetalingForPeriode>()
                        it.utbetaling.type shouldBe Utbetaling.UtbetalingsType.GJENOPPTA
                        it.simuleringsperiode shouldBe Periode.create(1.februar(2021), 31.desember(2021))
                    },
                )
            }
        }
    }
}
