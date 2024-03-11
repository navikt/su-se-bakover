package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import arrow.core.NonEmptyList
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import vilkår.uføre.domain.Uføregrunnlag
import økonomi.domain.utbetaling.KunneIkkeGenerereUtbetalingsstrategiForStans
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.Clock
import java.time.LocalDate

fun Sak.lagUtbetalingForStans(
    stansdato: LocalDate,
    behandler: NavIdentBruker,
    clock: Clock,
    aksepterKvitteringMedFeil: Boolean = false,
): Either<KunneIkkeGenerereUtbetalingsstrategiForStans, Utbetaling.UtbetalingForSimulering> {
    return Utbetalingsstrategi.Stans(
        sakId = id,
        saksnummer = saksnummer,
        fnr = fnr,
        eksisterendeUtbetalinger = utbetalinger,
        behandler = behandler,
        stansDato = stansdato,
        clock = clock,
        sakstype = type,
        aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
    ).generer()
}

fun Sak.lagUtbetalingForGjenopptak(
    saksbehandler: NavIdentBruker,
    clock: Clock,
    aksepterKvitteringMedFeil: Boolean = false,
): Either<Utbetalingsstrategi.Gjenoppta.Feil, Utbetaling.UtbetalingForSimulering> {
    return Utbetalingsstrategi.Gjenoppta(
        sakId = id,
        saksnummer = saksnummer,
        fnr = fnr,
        eksisterendeUtbetalinger = utbetalinger,
        behandler = saksbehandler,
        clock = clock,
        sakstype = type,
        aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
    ).generer()
}

/**
 * @param opphørsperiode Vil være behandlingen sin periode. Mens utbetalingslinjene vil inkludere evt. rekonstruerte linjer etter opphørsperioden.
 */
fun Sak.lagUtbetalingForOpphør(
    opphørsperiode: Periode,
    behandler: NavIdentBruker,
    clock: Clock,
    aksepterKvitteringMedFeil: Boolean = false,
): Utbetaling.UtbetalingForSimulering {
    return Utbetalingsstrategi.Opphør(
        sakId = id,
        saksnummer = saksnummer,
        fnr = fnr,
        eksisterendeUtbetalinger = utbetalinger,
        behandler = behandler,
        periode = opphørsperiode,
        clock = clock,
        sakstype = type,
        aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
    ).generate()
}

/**
 * @param aksepterKvitteringMedFeil Dette er kun for å korrige en utbetaling som feilet.
 */
fun Sak.lagNyUtbetaling(
    saksbehandler: NavIdentBruker,
    beregning: Beregning,
    clock: Clock,
    utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger,
    uføregrunnlag: NonEmptyList<Uføregrunnlag>?,
    aksepterKvitteringMedFeil: Boolean = false,
): Utbetaling.UtbetalingForSimulering {
    return when (type) {
        Sakstype.ALDER -> {
            lagNyUtbetalingAlder(
                saksbehandler = saksbehandler,
                beregning = beregning,
                clock = clock,
                utbetalingsinstruksjonForEtterbetaling = utbetalingsinstruksjonForEtterbetaling,
                aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
            )
        }

        Sakstype.UFØRE -> {
            lagNyUtbetalingUføre(
                saksbehandler = saksbehandler,
                beregning = beregning,
                clock = clock,
                utbetalingsinstruksjonForEtterbetaling = utbetalingsinstruksjonForEtterbetaling,
                uføregrunnlag = uføregrunnlag!!,
                aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
            )
        }
    }
}

/**
 * @param aksepterKvitteringMedFeil Dette er kun for å korrige en utbetaling som feilet.
 */
fun Sak.lagNyUtbetalingAlder(
    saksbehandler: NavIdentBruker,
    beregning: Beregning,
    clock: Clock,
    utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger,
    aksepterKvitteringMedFeil: Boolean = false,
): Utbetaling.UtbetalingForSimulering {
    return when (type) {
        Sakstype.ALDER -> {
            Utbetalingsstrategi.NyAldersUtbetaling(
                sakId = id,
                saksnummer = saksnummer,
                fnr = fnr,
                eksisterendeUtbetalinger = utbetalinger,
                behandler = saksbehandler,
                beregning = beregning,
                clock = clock,
                kjøreplan = utbetalingsinstruksjonForEtterbetaling,
                sakstype = type,
                aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
            ).generate()
        }

        else -> throw IllegalArgumentException("Ugyldig type:$type for aldersutbetaling")
    }
}

/**
 * @param aksepterKvitteringMedFeil Dette er kun for å korrige en utbetaling som feilet.
 */
fun Sak.lagNyUtbetalingUføre(
    saksbehandler: NavIdentBruker,
    beregning: Beregning,
    clock: Clock,
    utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger,
    uføregrunnlag: NonEmptyList<Uføregrunnlag>,
    aksepterKvitteringMedFeil: Boolean = false,
): Utbetaling.UtbetalingForSimulering {
    return when (type) {
        Sakstype.UFØRE -> {
            Utbetalingsstrategi.NyUføreUtbetaling(
                sakId = id,
                saksnummer = saksnummer,
                fnr = fnr,
                eksisterendeUtbetalinger = utbetalinger,
                behandler = saksbehandler,
                beregning = beregning,
                uføregrunnlag = uføregrunnlag,
                clock = clock,
                kjøreplan = utbetalingsinstruksjonForEtterbetaling,
                sakstype = type,
                aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
            ).generate()
        }

        else -> throw IllegalArgumentException("Ugyldig type:$type for uføreutbetaling")
    }
}

fun Sak.hentGjeldendeUtbetaling(
    forDato: LocalDate,
): Either<Utbetalinger.FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
    return this.utbetalinger.hentGjeldendeUtbetaling(forDato = forDato)
}
