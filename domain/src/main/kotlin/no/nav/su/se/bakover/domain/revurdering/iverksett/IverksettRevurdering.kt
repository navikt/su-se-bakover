package no.nav.su.se.bakover.domain.revurdering.iverksett

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.left
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.iverksett.innvilg.iverksettInnvilgetRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.opphør.iverksettOpphørtRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock
import java.util.UUID

fun Sak.iverksettRevurdering(
    revurderingId: UUID,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil, IverksettRevurderingResponse<VedtakSomKanRevurderes.EndringIYtelse>> {
    return either.eager {
        val revurdering = finnRevurderingOgValiderTilstand(revurderingId).bind()
        when (revurdering) {
            is RevurderingTilAttestering.Innvilget -> iverksettInnvilgetRevurdering(
                revurdering = revurdering,
                attestant = attestant,
                clock = clock,
                simuler = simuler,
            )

            is RevurderingTilAttestering.Opphørt -> iverksettOpphørtRevurdering(
                revurdering = revurdering,
                attestant = attestant,
                clock = clock,
                simuler = simuler,
            )
        }.bind()
    }
}

private fun Sak.finnRevurderingOgValiderTilstand(
    revurderingId: UUID,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil, RevurderingTilAttestering> {
    return hentRevurdering(revurderingId)
        .mapLeft { KunneIkkeIverksetteRevurdering.Saksfeil.FantIkkeRevurdering }
        .map {
            (it as? RevurderingTilAttestering) ?: return KunneIkkeIverksetteRevurdering.Saksfeil.UgyldigTilstand(
                fra = it::class,
                til = IverksattRevurdering::class,
            ).left()
        }
}
