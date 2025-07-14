package no.nav.su.se.bakover.domain.revurdering.iverksett.innvilg

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.simulering.kontrollsimuler
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.fromRevurderingInnvilget
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.oppdaterRevurdering
import vedtak.domain.VedtakSomKanRevurderes
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.time.Clock

internal fun Sak.iverksettInnvilgetRevurdering(
    revurdering: RevurderingTilAttestering.Innvilget,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil, IverksettInnvilgetRevurderingResponse> {
    require(this.revurderinger.contains(revurdering))

    if (this.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg(revurdering.periode, revurdering.vedtakSomRevurderesMånedsvis, clock).isLeft()) {
        return KunneIkkeIverksetteRevurdering.Saksfeil.DetHarKommetNyeOverlappendeVedtak.left()
    }

    return revurdering.tilIverksatt(
        attestant = attestant,
        clock = clock,
    ).mapLeft {
        KunneIkkeIverksetteRevurdering.Saksfeil.Revurderingsfeil(it)
    }.flatMap { iverksattRevurdering ->
        lagNyUtbetaling(
            saksbehandler = attestant,
            beregning = iverksattRevurdering.beregning,
            clock = clock,
            utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            uføregrunnlag = iverksattRevurdering.hentUføregrunnlag(),
        ).let {
            kontrollsimuler(
                utbetalingForSimulering = it,
                simuler = simuler,
                saksbehandlersSimulering = iverksattRevurdering.simulering,
            )
        }.mapLeft { feil ->
            KunneIkkeIverksetteRevurdering.Saksfeil.KontrollsimuleringFeilet(feil)
        }.map { simulertUtbetaling ->
            VedtakSomKanRevurderes.fromRevurderingInnvilget(
                revurdering = iverksattRevurdering,
                utbetalingId = simulertUtbetaling.id,
                clock = clock,
            ).let { vedtak ->
                IverksettInnvilgetRevurderingResponse(
                    sak = oppdaterRevurdering(iverksattRevurdering)
                        .copy(
                            vedtakListe = vedtakListe.filterNot { it.id == vedtak.id } + vedtak,
                            // TODO jah: Her legger vi til en [SimulertUtbetaling] istedenfor en [OversendtUtbetaling] det kan i første omgang klusse til testdataene.
                            utbetalinger = utbetalinger + simulertUtbetaling,
                        ),
                    vedtak = vedtak,
                    utbetaling = simulertUtbetaling,
                )
            }
        }
    }
}
