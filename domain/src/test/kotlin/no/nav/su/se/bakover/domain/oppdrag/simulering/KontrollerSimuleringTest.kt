package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.simulering
import no.nav.su.se.bakover.test.simulertDetaljFeilutbetaling
import no.nav.su.se.bakover.test.simulertDetaljOrdinær
import no.nav.su.se.bakover.test.simulertDetaljTidligereUtbetalt
import no.nav.su.se.bakover.test.simulertDetaljTilbakeføring
import no.nav.su.se.bakover.test.simulertPeriode
import no.nav.su.se.bakover.test.simulertUtbetaling
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class KontrollerSimuleringTest {

    @Test
    fun `kontroll av simulering går bra dersom simulering ikke inneholder noen utbetalinger`() {
        val eksisterendeUtetaling = eksisterendeUtbetaling(
            periode = januar(2021),
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 5000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )
        val simulertUtbetaling = simulertUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = eksisterendeUtetaling.sisteUtbetalingslinje(),
                    virkningsperiode = Periode.create(1.januar(2021), eksisterendeUtetaling.sisteUtbetalingslinje().tilOgMed),
                    clock = Clock.systemUTC(),
                ),
            ),
            simulering = simulering(
                januar(2021),
                simulertePerioder = emptyList(),
            ),
        )

        KontrollerSimulering(
            simulertUtbetaling = simulertUtbetaling,
            eksisterendeUtbetalinger = listOf(eksisterendeUtetaling),
            clock = fixedClock,
        ).resultat shouldBe simulertUtbetaling.right()
    }

    @Test
    fun `kontroll av simulering går bra dersom ingen utbetalinger eksisterer fra før`() {
        val simulertUtbetaling = simulertUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
            simulering = simulering(januar(2021)),
        )

        KontrollerSimulering(
            simulertUtbetaling = simulertUtbetaling,
            eksisterendeUtbetalinger = listOf(),
            clock = fixedClock,
        ).resultat shouldBe simulertUtbetaling.right()
    }

    @Test
    fun `svarer med feil dersom beløpet for simulert måned ikke stemmer overens med beløp fra tidslinje`() {
        val simulertUtbetaling = simulertUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 5000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
            simulering = simulering(januar(2021)),
        )

        KontrollerSimulering(
            simulertUtbetaling = simulertUtbetaling,
            eksisterendeUtbetalinger = listOf(),
            clock = fixedClock,
        ).resultat shouldBe KontrollerSimulering.KontrollAvSimuleringFeilet.SimulertBeløpErForskjelligFraBeløpPåTidslinje.left()
    }

    @Test
    fun `kontroll av etterbetaling går bra dersom beløp stemmer overens med tidslinje`() {
        val periode = januar(2021)

        val eksisterendeUtetaling = eksisterendeUtbetaling(
            periode = periode,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 5000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )

        val simulertUtbetaling = simulertUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt.plus(1, ChronoUnit.SECONDS),
                    fraOgMed = periode.fraOgMed,
                    tilOgMed = periode.tilOgMed,
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
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
            clock = fixedClock.plus(2, ChronoUnit.SECONDS),
        ).resultat shouldBe simulertUtbetaling.right()
    }

    @Test
    fun `svarer med feil dersom simulering inneholder feilutbetaling`() {
        val periode = januar(2021)

        val eksisterendeUtetaling = eksisterendeUtbetaling(
            periode = periode,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 5000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )

        val simulertUtbetaling = simulertUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = eksisterendeUtetaling.sisteUtbetalingslinje(),
                    virkningsperiode = Periode.create(1.januar(2021), eksisterendeUtetaling.sisteUtbetalingslinje().tilOgMed),
                    clock = fixedClock,
                ),
            ),
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
        ).resultat shouldBe KontrollerSimulering.KontrollAvSimuleringFeilet.SimuleringInneholderFeilutbetaling.left()
    }

    private fun simulertUtbetaling(
        utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
        simulering: Simulering,
    ): Utbetaling.SimulertUtbetaling {
        return Utbetaling.UtbetalingForSimulering(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(9999),
            fnr = fnr,
            utbetalingslinjer = utbetalingslinjer,
            behandler = NavIdentBruker.Saksbehandler("saksa"),
            avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
            sakstype = Sakstype.UFØRE,
        ).toSimulertUtbetaling(
            simulering = simulering,
        )
    }

    private fun eksisterendeUtbetaling(
        periode: Periode,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
    ): Utbetaling.OversendtUtbetaling.UtenKvittering = simulertUtbetaling(
        utbetalingslinjer,
        simulering(periode),
    ).toOversendtUtbetaling(Utbetalingsrequest(""))
}
