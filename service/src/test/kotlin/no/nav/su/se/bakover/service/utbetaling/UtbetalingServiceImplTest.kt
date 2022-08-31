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
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simuleringNy
import no.nav.su.se.bakover.test.simuleringOpphørt
import no.nav.su.se.bakover.test.simuleringStans
import no.nav.su.se.bakover.test.utbetalingslinje
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.UUID

internal class UtbetalingServiceImplTest {

    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val fnr = Fnr("12345678910")

    private val attestant = NavIdentBruker.Attestant("SU")

    private val avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt)

    private val dummyUtbetalingslinjer = nonEmptyListOf(
        utbetalingslinje(
            periode = januar(2020),
            beløp = 0,
        ),
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        id = UUID30.randomUUID(),
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = dummyUtbetalingslinjer,
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel,
        sakstype = Sakstype.UFØRE,
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
                Utbetaling.UtbetalingForSimulering(
                    id = it.id,
                    opprettet = it.opprettet,
                    sakId = it.sakId,
                    saksnummer = it.saksnummer,
                    fnr = it.fnr,
                    utbetalingslinjer = it.utbetalingslinjer,
                    behandler = it.behandler,
                    avstemmingsnøkkel = it.avstemmingsnøkkel,
                    sakstype = Sakstype.UFØRE,
                ).toSimulertUtbetaling(
                    simulering = it.simulering,
                ).toOversendtUtbetaling(
                    oppdragsmelding = it.utbetalingsrequest,
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
                Utbetaling.UtbetalingForSimulering(
                    id = it.id,
                    opprettet = it.opprettet,
                    sakId = it.sakId,
                    saksnummer = it.saksnummer,
                    fnr = it.fnr,
                    utbetalingslinjer = it.utbetalingslinjer,
                    behandler = it.behandler,
                    avstemmingsnøkkel = it.avstemmingsnøkkel,
                    sakstype = Sakstype.UFØRE,
                ).toSimulertUtbetaling(
                    simulering = it.simulering,
                ).toOversendtUtbetaling(
                    oppdragsmelding = it.utbetalingsrequest,
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

    @Nested
    inner class hentGjeldendeUtbetaling {
        @Test
        fun `henter utbetalingslinjen som gjelder for datoen som sendes inn`() {
            val expectedGjeldendeUtbetalingslinje =
                utbetalingslinje(
                    id = UUID30.randomUUID(),
                    periode = januar(2020),
                    beløp = 53821,
                )

            val utbetalingRepoMock = mock<UtbetalingRepo> {
                on { hentUtbetalinger(any()) } doReturn listOf(
                    utbetalingForSimulering.copy(
                        utbetalingslinjer = nonEmptyListOf(
                            expectedGjeldendeUtbetalingslinje,
                            utbetalingslinje(
                                periode = februar(2020),
                                beløp = 53821,
                            ),
                            utbetalingslinje(
                                periode = mars(2020),
                                beløp = 53821,
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
                simuleringClient = mock {
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
                        it.utbetaling.erStans() shouldBe true
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
                simuleringClient = mock {
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
                simuleringClient = mock {
                    on { simulerUtbetaling(any()) } doReturn simuleringNy(
                        beregning = (sak.vedtakListe.single { it.id == stans.behandling.tilRevurdering } as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling).beregning,
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
                        it.utbetaling.erReaktivering() shouldBe true
                        it.simuleringsperiode shouldBe Periode.create(1.februar(2021), 31.desember(2021))
                    },
                )
            }
        }
    }
}
