package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.simulering
import no.nav.su.se.bakover.test.simulertDetaljFeilutbetaling
import no.nav.su.se.bakover.test.simulertDetaljOrdinær
import no.nav.su.se.bakover.test.simulertDetaljTidligereUtbetalt
import no.nav.su.se.bakover.test.simulertDetaljTilbakeføring
import no.nav.su.se.bakover.test.simulertPeriode
import no.nav.su.se.bakover.test.simulertUtbetaling
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

internal class KontrollerSimuleringTest {

    @Test
    fun `kontroll av simulering går bra dersom simulering ikke inneholder noen utbetalinger`() {
        val eksisterendeUtetaling = eksisterendeUtbetaling(
            periode = Periode.create(1.januar(2021), 31.januar(2021)),
            type = Utbetaling.UtbetalingsType.NY,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 5000,
                ),
            ),
        )
        val simulertUtbetaling = simulertUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = eksisterendeUtetaling.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.januar(2021),
                    clock = Clock.systemUTC(),
                ),
            ),
            type = Utbetaling.UtbetalingsType.OPPHØR,
            simulering = simulering(
                Periode.create(1.januar(2021), 31.januar(2021)),
                simulertePerioder = emptyList(),
            ),
        )

        KontrollerSimulering(
            simulertUtbetaling = simulertUtbetaling,
            eksisterendeUtbetalinger = listOf(eksisterendeUtetaling),
            clock = fixedClock,
        ).resultat shouldBeRight simulertUtbetaling
    }

    @Test
    fun `kontroll av simulering går bra dersom ingen utbetalinger eksisterer fra før`() {
        val simulertUtbetaling = simulertUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            simulering = simulering(Periode.create(1.januar(2021), 31.januar(2021))),
        )

        KontrollerSimulering(
            simulertUtbetaling = simulertUtbetaling,
            eksisterendeUtbetalinger = listOf(),
            clock = fixedClock,
        ).resultat shouldBeRight simulertUtbetaling
    }

    @Test
    fun `svarer med feil dersom beløpet for simulert måned ikke stemmer overens med beløp fra tidslinje`() {
        val simulertUtbetaling = simulertUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 5000,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            simulering = simulering(Periode.create(1.januar(2021), 31.januar(2021))),
        )

        KontrollerSimulering(
            simulertUtbetaling = simulertUtbetaling,
            eksisterendeUtbetalinger = listOf(),
            clock = fixedClock,
        ).resultat shouldBeLeft KontrollerSimulering.KontrollAvSimuleringFeilet.SimulertBeløpErForskjelligFraBeløpPåTidslinje
    }

    @Test
    fun `kontroll av etterbetaling går bra dersom beløp stemmer overens med tidslinje`() {
        val periode = Periode.create(1.januar(2021), 31.januar(2021))

        val eksisterendeUtetaling = eksisterendeUtbetaling(
            periode = Periode.create(1.januar(2021), 31.januar(2021)),
            type = Utbetaling.UtbetalingsType.NY,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 5000,
                ),
            ),
        )

        val simulertUtbetaling = simulertUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    fraOgMed = periode.fraOgMed,
                    tilOgMed = periode.tilOgMed,
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            simulering = simulering(
                periode,
                simulertePerioder = listOf(
                    simulertPeriode(
                        periode = periode,
                        simulerteUtbetalinger = listOf(
                            simulertUtbetaling(
                                periode = periode,
                                simulertDetaljer = listOf(
                                    simulertDetaljOrdinær(
                                        periode = periode,
                                        beløp = 15000,
                                    ),
                                    simulertDetaljTilbakeføring(
                                        periode = periode,
                                        beløp = 5000,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KontrollerSimulering(
            simulertUtbetaling = simulertUtbetaling,
            eksisterendeUtbetalinger = listOf(eksisterendeUtetaling),
            clock = fixedClock,
        ).resultat shouldBeRight simulertUtbetaling
    }

    @Test
    fun `svarer med feil dersom simulering inneholder feilutbetaling`() {
        val periode = Periode.create(1.januar(2021), 31.januar(2021))

        val eksisterendeUtetaling = eksisterendeUtbetaling(
            periode = periode,
            type = Utbetaling.UtbetalingsType.NY,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 5000,
                ),
            ),
        )

        val simulertUtbetaling = simulertUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = eksisterendeUtetaling.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.januar(2021),
                    clock = fixedClock,
                ),
            ),
            type = Utbetaling.UtbetalingsType.OPPHØR,
            simulering = simulering(
                periode,
                simulertePerioder = listOf(
                    simulertPeriode(
                        periode = periode,
                        simulerteUtbetalinger = listOf(
                            simulertUtbetaling(
                                periode = periode,
                                simulertDetaljer = listOf(
                                    simulertDetaljFeilutbetaling(
                                        periode = periode,
                                        beløp = 5000,
                                    ),
                                    simulertDetaljTilbakeføring(
                                        periode = periode,
                                        beløp = 5000,
                                    ),
                                    simulertDetaljTidligereUtbetalt(
                                        periode = periode,
                                        beløp = 5000,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KontrollerSimulering(
            simulertUtbetaling = simulertUtbetaling,
            eksisterendeUtbetalinger = listOf(eksisterendeUtetaling),
            clock = fixedClock,
        ).resultat shouldBeLeft KontrollerSimulering.KontrollAvSimuleringFeilet.SimuleringInneholderFeilutbetaling
    }

    private fun simulertUtbetaling(
        utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
        type: Utbetaling.UtbetalingsType,
        simulering: Simulering,
    ): Utbetaling.SimulertUtbetaling = Utbetaling.SimulertUtbetaling(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(9999),
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        type = type,
        behandler = NavIdentBruker.Saksbehandler("saksa"),
        avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now()),
        simulering = simulering,
    )

    private fun eksisterendeUtbetaling(
        periode: Periode,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
        type: Utbetaling.UtbetalingsType,
    ): Utbetaling.OversendtUtbetaling.UtenKvittering = simulertUtbetaling(
        utbetalingslinjer,
        type,
        simulering(periode),
    ).toOversendtUtbetaling(Utbetalingsrequest(""))
}
