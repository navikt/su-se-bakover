package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger

/**
 * Hjelpefunksjon for å simulere utbetalinger.
 * Skal ikke brukes til å simulere reaktivering. Se heller [simulerReakUtbetaling]
 * Utføres under behandlingen.
 * Dersom vi klarer og simulere, vil vi sammenligne simuleringen med utbetalingen.
 */
fun simulerUtbetaling(
    utbetalingForSimulering: Utbetaling.UtbetalingForSimulering,
    simuler: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<SimuleringFeilet, Simuleringsresultat> {
    val simulertUtbetaling = simuler(utbetalingForSimulering)
        .getOrElse { return it.left() }

    return kryssjekkSimuleringMotUtbetaling(simulertUtbetaling).fold(
        { Simuleringsresultat.MedForskjeller(simulertUtbetaling, it) },
        { Simuleringsresultat.UtenForskjeller(simulertUtbetaling) },
    ).right()
}

/**
 * Spesialtilfelle av [simulerUtbetaling] kun for reaktivering.
 */
fun simulerReakUtbetaling(
    tidligereUtbetalinger: Utbetalinger,
    utbetalingForSimulering: Utbetaling.UtbetalingForSimulering,
    simuler: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<SimuleringFeilet, Simuleringsresultat> {
    val simulertUtbetaling = simuler(utbetalingForSimulering)
        .getOrElse { return it.left() }

    return kryssjekkSimuleringMotReakUtbetaling(
        tidligereUtbetalinger = tidligereUtbetalinger,
        simulertUtbetaling = simulertUtbetaling,
    ).fold(
        { Simuleringsresultat.MedForskjeller(simulertUtbetaling, it) },
        { Simuleringsresultat.UtenForskjeller(simulertUtbetaling) },
    ).right()
}
