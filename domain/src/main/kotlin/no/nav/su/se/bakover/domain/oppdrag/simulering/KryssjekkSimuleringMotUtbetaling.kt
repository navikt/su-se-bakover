package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOfOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.simulering.ForskjellerMellomUtbetalingOgSimulering
import økonomi.domain.simulering.ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode
import økonomi.domain.simulering.Simulering
import økonomi.domain.utbetaling.TidslinjeForUtbetalinger
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalinger

/**
 * En sjekk som gjøres for å gi saksbehandler tilbakemelding om simuleringen stemmer overens med utbetalingslinjene som kommer til å bli sendt.
 * Vi gjør ingen sjekker utover utbetalingsperiodene til [simulertUtbetaling].
 * Dersom perioden til simuleringen er ulik utbetalingsperioden logger vi en error. TODO jah: På sikt bør vi kanskje feile dersom dette skjer.
 *
 * @param simulertUtbetaling Utbetalingen som har blitt simulert
 *
 */
fun kryssjekkSimuleringMotUtbetaling(
    tidligereUtbetalinger: Utbetalinger,
    simulertUtbetaling: Utbetaling.SimulertUtbetaling,
    log: Logger = LoggerFactory.getLogger("KryssjekkSimuleringMotUtbetaling.kt"),
): Either<ForskjellerMellomUtbetalingOgSimulering, Unit> {
    val saksnummer = simulertUtbetaling.saksnummer
    if (simulertUtbetaling.periode != simulertUtbetaling.simulering.periode()) {
        log.info(
            "Simuleringens periode er ulik utbetalingsperioden under kryssjekk av simulering og utbetaling. Ved 0-utbetalinger langt fram i tid, kan Oppdrag i noen tilfeller hoppe over og simulere de.  Saksnummer: $saksnummer, Utbetalingsperode: ${simulertUtbetaling.periode}, Simuleringsperiode: ${simulertUtbetaling.simulering.periode()}",
            RuntimeException("Genererer en stacktrace for enklere debugging."),
        )
    }
    sjekkUtbetalingMotSimulering(
        simulering = simulertUtbetaling.simulering,
        // En reaktivering inneholder kun én utbetalingslinje som sier at den tidligere stansen skal reaktiveres. Simuleringen vil vise beløpene som vil utbetales. Vi må derfor legge sammen tidligere utbetalinger + denne utbetalingen og krympe den igjen før vi kan sammenligne.
        utbetalingslinjePåTidslinjer = TidslinjeForUtbetalinger.fra(tidligereUtbetalinger + simulertUtbetaling)!!
            .krympTilPeriode(simulertUtbetaling.periode)!!,
    ).getOrElse {
        log.error(
            "Feil ved kryssjekk av utbetaling og simulering for saksnummer $saksnummer. Se sikkerlogg for mer kontekst. Feil: $it",
            RuntimeException("Genererer en stacktrace for enklere debugging."),
        )
        sikkerLogg.error("Feil ved kryssjekk av utbetaling og simulering for saksnummer $saksnummer. Se vanlig logg for stacktrace. Feil: $it, Utbetaling med simulering: $simulertUtbetaling")
        return it.left()
    }
    return Unit.right()
}

private fun sjekkUtbetalingMotSimulering(
    simulering: Simulering,
    utbetalingslinjePåTidslinjer: TidslinjeForUtbetalinger,
): Either<ForskjellerMellomUtbetalingOgSimulering, Unit> {
    val forskjeller = mutableListOf<ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode>()
    // Siden vi kan ha overlappende perioder med dagens utbetalingslinjealgoritme, så må vi lage en tidslinje.
    if (simulering.erAlleMånederUtenUtbetaling()) {
        // Spesialtilfelle der vi har fått tom respons fra oppdrag. Som betyr at vi ikke har utbetalt noe og ikke skal utbetale noe.
        // Da sjekker vi bare at alle linjene er 0
        utbetalingslinjePåTidslinjer.forEach {
            if (it.beløp > 0) {
                forskjeller.add(
                    ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UtbetalingslinjeVarIkke0(
                        periode = it.periode,
                        beløp = it.beløp,
                    ),
                )
            }
        }
    } else {
        utbetalingslinjePåTidslinjer.forEach { linje ->
            // Merk at linjene starter første dagen en måned og slutter siste dagen en måned (kan strekke seg over flere måneder).
            // Mens simuleringsperiodene kan starte/slutte vilkårlige dager i måneden. Usikker på om den kan strekke seg på tvers av måneder.
            val simuleringsperioderOgBeløp =
                simulering.hentTotalUtbetaling().filter { linje.periode.overlapper(it.periode) }
            val simuleringsperiode = simuleringsperioderOgBeløp.map { it.periode }.minAndMaxOfOrNull()
            val simuleringsbeløp = simuleringsperioderOgBeløp.sumOf { it.beløp.sum() }
            if (simuleringsperiode != linje.periode) {
                forskjeller.add(
                    ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UlikPeriode(
                        utbetalingsperiode = linje.periode,
                        simuleringsperiode = simuleringsperiode,
                        simulertBeløp = simuleringsbeløp,
                        utbetalingsbeløp = linje.beløp,
                    ),
                )
            }
            if (simuleringsbeløp != linje.beløp) {
                ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode.UliktBeløp(
                    periode = linje.periode,
                    simulertBeløp = simuleringsbeløp,
                    utbetalingsbeløp = linje.beløp,
                )
            }
        }
    }
    return when (val f = forskjeller.toNonEmptyListOrNull()) {
        null -> Unit.right()
        else -> ForskjellerMellomUtbetalingOgSimulering(f).left()
    }
}
