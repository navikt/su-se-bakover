package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import arrow.core.NonEmptyList
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.hentGjeldendeUtbetaling
import vilkår.uføre.domain.Uføregrunnlag
import økonomi.domain.utbetaling.KunneIkkeGenerereUtbetalingsstrategiForStans
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.time.Clock
import java.time.LocalDate

fun Sak.lagUtbetalingForStans(
    stansdato: LocalDate,
    behandler: NavIdentBruker,
    clock: Clock,
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
    ).generer()
}

fun Sak.lagUtbetalingForGjenopptak(
    saksbehandler: NavIdentBruker,
    clock: Clock,
): Either<Utbetalingsstrategi.Gjenoppta.Feil, Utbetaling.UtbetalingForSimulering> {
    return Utbetalingsstrategi.Gjenoppta(
        sakId = id,
        saksnummer = saksnummer,
        fnr = fnr,
        eksisterendeUtbetalinger = utbetalinger,
        behandler = saksbehandler,
        clock = clock,
        sakstype = type,
    ).generer()
}

/**
 * @param opphørsperiode Vil være behandlingen sin periode. Mens utbetalingslinjene vil inkludere evt. rekonstruerte linjer etter opphørsperioden.
 */
fun Sak.lagUtbetalingForOpphør(
    opphørsperiode: Periode,
    behandler: NavIdentBruker,
    clock: Clock,
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
    ).generate()
}

fun Sak.lagNyUtbetaling(
    saksbehandler: NavIdentBruker,
    beregning: Beregning,
    clock: Clock,
    utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger,
    uføregrunnlag: NonEmptyList<Uføregrunnlag>?,
): Utbetaling.UtbetalingForSimulering {
    return when (type) {
        Sakstype.ALDER -> {
            lagNyUtbetalingAlder(
                saksbehandler = saksbehandler,
                beregning = beregning,
                clock = clock,
                utbetalingsinstruksjonForEtterbetaling = utbetalingsinstruksjonForEtterbetaling,
            )
        }

        Sakstype.UFØRE -> {
            lagNyUtbetalingUføre(
                saksbehandler = saksbehandler,
                beregning = beregning,
                clock = clock,
                utbetalingsinstruksjonForEtterbetaling = utbetalingsinstruksjonForEtterbetaling,
                uføregrunnlag = uføregrunnlag!!,
            )
        }
    }
}

fun Sak.lagNyUtbetalingAlder(
    saksbehandler: NavIdentBruker,
    beregning: Beregning,
    clock: Clock,
    utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger,
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
            ).generate()
        }

        else -> throw IllegalArgumentException("Ugyldig type:$type for aldersutbetaling")
    }
}

fun Sak.lagNyUtbetalingUføre(
    saksbehandler: NavIdentBruker,
    beregning: Beregning,
    clock: Clock,
    utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger,
    uføregrunnlag: NonEmptyList<Uføregrunnlag>,
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
