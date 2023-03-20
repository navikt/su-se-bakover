package no.nav.su.se.bakover.domain.sak

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvTidslinjeOgSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkSaksbehandlersOgAttestantsSimulering
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkTidslinjerOgSimulering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
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

fun Sak.lagNyUtbetaling(
    saksbehandler: NavIdentBruker,
    beregning: Beregning,
    clock: Clock,
    utbetalingsinstruksjonForEtterbetaling: UtbetalingsinstruksjonForEtterbetalinger,
    uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?,
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
    uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>,
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

fun Sak.simulerUtbetaling(
    utbetalingForSimulering: Utbetaling.UtbetalingForSimulering,
    periode: Periode,
    simuler: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    kontrollerMotTidligereSimulering: Simulering?,
): Either<SimulerUtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
    return simuler(utbetalingForSimulering, periode)
        .mapLeft {
            SimulerUtbetalingFeilet.FeilVedSimulering(it)
        }
        .map { simulertUtbetaling ->
            KryssjekkTidslinjerOgSimulering.sjekk(
                underArbeidEndringsperiode = periode,
                underArbeid = utbetalingForSimulering,
                eksisterende = utbetalinger,
                simuler = simuler,
            ).getOrElse {
                return SimulerUtbetalingFeilet.FeilVedKryssjekkAvTidslinjeOgSimulering(it).left()
            }
            if (kontrollerMotTidligereSimulering != null) {
                KryssjekkSaksbehandlersOgAttestantsSimulering(
                    saksbehandlersSimulering = kontrollerMotTidligereSimulering,
                    attestantsSimulering = simulertUtbetaling,
                ).sjekk().getOrElse {
                    return SimulerUtbetalingFeilet.FeilVedKryssjekkAvSaksbehandlerOgAttestantsSimulering(it).left()
                }
            }
            simulertUtbetaling
        }
}

sealed interface SimulerUtbetalingFeilet {
    data class FeilVedSimulering(val feil: SimuleringFeilet) : SimulerUtbetalingFeilet
    data class FeilVedKryssjekkAvTidslinjeOgSimulering(val feil: KryssjekkAvTidslinjeOgSimuleringFeilet) : SimulerUtbetalingFeilet
    data class FeilVedKryssjekkAvSaksbehandlerOgAttestantsSimulering(val feil: KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet) : SimulerUtbetalingFeilet
}
