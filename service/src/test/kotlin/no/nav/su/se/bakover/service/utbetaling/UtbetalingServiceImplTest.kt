package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.toPeriode
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.kvittering
import no.nav.su.se.bakover.test.opphørUtbetalingSimulert
import no.nav.su.se.bakover.test.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simuleringNy
import no.nav.su.se.bakover.test.simuleringStans
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
                utbetalingId = UUID30.randomUUID(),
                kvittering = kvittering(),
            ) shouldBe FantIkkeUtbetaling.left()
        }
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer ikke fra før`() {
        val utbetalingUtenKvittering = oversendtUtbetalingUtenKvittering()
        val utbetalingMedKvittering = utbetalingUtenKvittering.toKvittertUtbetaling(kvittering())

        UtbetalingServiceAndMocks(
            utbetalingRepo = mock {
                on { hentUtbetaling(any<UUID30>()) } doReturn utbetalingUtenKvittering
            },
        ).let {
            it.service.oppdaterMedKvittering(
                utbetalingId = utbetalingUtenKvittering.id,
                kvittering = kvittering(),
            ).getOrFail() shouldBe utbetalingMedKvittering

            verify(it.utbetalingRepo).oppdaterMedKvittering(utbetalingMedKvittering)
        }
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer fra før`() {
        val utbetalingMedKvittering = oversendtUtbetalingMedKvittering()

        UtbetalingServiceAndMocks(
            utbetalingRepo = mock {
                on { hentUtbetaling(any<UUID30>()) } doReturn utbetalingMedKvittering
            },
        ).let {
            it.service.oppdaterMedKvittering(
                utbetalingId = utbetalingMedKvittering.id,
                kvittering = kvittering(),
            ).getOrFail() shouldBe utbetalingMedKvittering

            verify(it.utbetalingRepo, never()).oppdaterMedKvittering(any())
        }
    }

    @Nested
    inner class hentGjeldendeUtbetaling {
        @Test
        fun `henter utbetalingslinjen som gjelder for datoen som sendes inn`() {
            val (sak, _) = iverksattSøknadsbehandlingUføre(
                customGrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = januar(2021),
                        arbeidsinntekt = 1000.0
                    ),
                    fradragsgrunnlagArbeidsinntekt(
                        periode = februar(2021),
                        arbeidsinntekt = 2000.0
                    ),
                    fradragsgrunnlagArbeidsinntekt(
                        periode = mars(2021),
                        arbeidsinntekt = 3000.0
                    )
                )
            )

            UtbetalingServiceAndMocks(
                utbetalingRepo = mock {
                    on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
                }
            ).also {
                it.service.hentGjeldendeUtbetaling(
                    sakId = sak.id,
                    forDato = 15.januar(2021)
                ) shouldBe UtbetalingslinjePåTidslinje.Ny(
                    kopiertFraId = sak.utbetalinger.first().utbetalingslinjer[0].id,
                    opprettet = sak.utbetalinger.first().utbetalingslinjer[0].opprettet,
                    periode = januar(2021),
                    beløp = 19946
                ).right()

                it.service.hentGjeldendeUtbetaling(
                    sakId = sak.id,
                    forDato = 29.mars(2021)
                ) shouldBe UtbetalingslinjePåTidslinje.Ny(
                    kopiertFraId = sak.utbetalinger.first().utbetalingslinjer[2].id,
                    opprettet = sak.utbetalinger.first().utbetalingslinjer[2].opprettet,
                    periode = mars(2021),
                    beløp = 17946
                ).right()
            }
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
            val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()

            UtbetalingServiceAndMocks(
                sakService = mock {
                    on { hentSak(any<UUID>()) } doReturn sak.right()
                },
                simuleringClient = mock {
                    on { simulerUtbetaling(any()) } doReturn opphørUtbetalingSimulert(
                        sakOgBehandling = sak to vedtak.behandling,
                        opphørsperiode = 1.februar(2021).rangeTo(vedtak.periode.tilOgMed).toPeriode(),
                        clock = fixedClock,
                    ).simulering.right()
                },
                clock = fixedClock,
            ).let {
                it.service.simulerOpphør(
                    request = SimulerUtbetalingRequest.Opphør(
                        sakId = sak.id,
                        saksbehandler = saksbehandler,
                        opphørsperiode = 1.februar(2021).rangeTo(vedtak.periode.tilOgMed).toPeriode(),
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
