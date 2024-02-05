package no.nav.su.se.bakover.domain.revurdering.iverksett

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.iverksett.innvilg.iverksettInnvilgetRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.opphør.medUtbetaling.iverksettOpphørtRevurderingMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.Revurderingsvedtak
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import java.time.Clock

fun Sak.iverksettRevurdering(
    revurderingId: RevurderingId,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil, IverksettRevurderingResponse<Revurderingsvedtak>> {
    return either {
        val revurdering = finnRevurderingOgValiderTilstand(revurderingId).bind()
        when (revurdering) {
            is RevurderingTilAttestering.Innvilget -> iverksettInnvilgetRevurdering(
                revurdering = revurdering,
                attestant = attestant,
                clock = clock,
                simuler = simuler,
            )

            is RevurderingTilAttestering.Opphørt -> {
                iverksettOpphørtRevurderingMedUtbetaling(
                    revurdering = revurdering,
                    attestant = attestant,
                    clock = clock,
                    simuler = simuler,
                )
            }
        }.bind()
    }
}

private fun Sak.finnRevurderingOgValiderTilstand(
    revurderingId: RevurderingId,
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
