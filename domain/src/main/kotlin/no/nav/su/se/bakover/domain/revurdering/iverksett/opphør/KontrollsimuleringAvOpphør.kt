package no.nav.su.se.bakover.domain.revurdering.iverksett.opphør

import arrow.core.Either
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.kontrollsimuler
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForOpphør
import økonomi.domain.simulering.Simulering
import java.time.Clock

internal fun Sak.kontrollsimulerOpphør(
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    periode: Periode,
    saksbehandlersSimulering: Simulering,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil, Utbetaling.SimulertUtbetaling> {
    return lagUtbetalingForOpphør(
        opphørsperiode = periode,
        behandler = attestant,
        clock = clock,
    ).let {
        kontrollsimuler(
            utbetalingForSimulering = it,
            simuler = simuler,
            saksbehandlersSimulering = saksbehandlersSimulering,
        )
    }.mapLeft {
        KunneIkkeIverksetteRevurdering.Saksfeil.KontrollsimuleringFeilet(it)
    }
}
