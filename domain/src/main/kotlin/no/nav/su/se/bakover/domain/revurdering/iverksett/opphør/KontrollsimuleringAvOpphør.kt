package no.nav.su.se.bakover.domain.revurdering.iverksett.opphør

import arrow.core.Either
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForOpphør
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import java.time.Clock

internal fun Sak.kontrollsimuler(
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    periode: Periode,
    saksbehandlersSimulering: Simulering,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil, Utbetaling.SimulertUtbetaling> {
    return lagUtbetalingForOpphør(
        opphørsperiode = periode,
        behandler = attestant,
        clock = clock,
    ).let {
        simulerUtbetaling(
            utbetalingForSimulering = it,
            periode = periode,
            simuler = simuler,
            kontrollerMotTidligereSimulering = saksbehandlersSimulering,
        )
    }.mapLeft {
        KunneIkkeIverksetteRevurdering.Saksfeil.KontrollsimuleringFeilet(it)
    }
}
