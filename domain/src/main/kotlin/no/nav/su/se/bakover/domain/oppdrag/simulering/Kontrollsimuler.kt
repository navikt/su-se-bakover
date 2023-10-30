package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import økonomi.domain.simulering.Simulering

/**
 * Hjelpefunksjon for å sammenligne 2 simuleringer.
 * Gir kun mening dersom det går nok tid mellom simuleringen og oversending av utbetalingslinjene.
 * Merk at det ikke er kontrollsimuleringen sin oppgave og kryssjekke mot beregning/utbetalingslinjene, dette må gjøres under selve simuleringen.
 *
 * TODO jah: I utgangspunktet bør vi kunne ta inn en SimulertUtbetaling her, som brukes videre under iverksettelsen istedenfor at vi genererer en ny [Utbetaling.UtbetalingForSimulering]
 */
fun kontrollsimuler(
    utbetalingForSimulering: Utbetaling.UtbetalingForSimulering,
    simuler: (Utbetaling.UtbetalingForSimulering) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    saksbehandlersSimulering: Simulering,
): Either<KontrollsimuleringFeilet, Utbetaling.SimulertUtbetaling> {
    val simulertUtbetaling = simuler(utbetalingForSimulering).getOrElse {
        return KontrollsimuleringFeilet.KunneIkkeSimulere(it).left()
    }
    return KryssjekkSaksbehandlersOgAttestantsSimulering(
        saksbehandlersSimulering = saksbehandlersSimulering,
        attestantsSimulering = simulertUtbetaling,
    ).sjekk().mapLeft {
        KontrollsimuleringFeilet.Forskjeller(it)
    }.map {
        simulertUtbetaling
    }
}
