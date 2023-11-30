package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.simulering.Simuleringsresultat
import økonomi.domain.utbetaling.Utbetaling

/**
 * Hjelpefunksjon for å simulere utbetalinger.
 * Utføres under behandlingen.
 * Dersom vi klarer og simulere, vil vi sammenligne simuleringen med utbetalingen.
 */
fun simulerUtbetaling(
    tidligereUtbetalinger: Utbetalinger,
    utbetalingForSimulering: Utbetaling.UtbetalingForSimulering,
    simuler: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<SimuleringFeilet, Simuleringsresultat> {
    val simulertUtbetaling = simuler(utbetalingForSimulering)
        .getOrElse { return it.left() }

    return kryssjekkSimuleringMotUtbetaling(
        tidligereUtbetalinger = tidligereUtbetalinger,
        simulertUtbetaling = simulertUtbetaling,
    ).fold(
        { Simuleringsresultat.MedForskjeller(simulertUtbetaling, it) },
        { Simuleringsresultat.UtenForskjeller(simulertUtbetaling) },
    ).right()
}
