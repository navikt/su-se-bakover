package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import java.time.Clock
import java.time.LocalDate

fun Sak.lagUtbetalingForStans(
    stansdato: LocalDate,
    behandler: NavIdentBruker,
    clock: Clock,
): Either<Utbetalingsstrategi.Stans.Feil, Utbetaling.UtbetalingForSimulering> {
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
