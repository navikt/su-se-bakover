package no.nav.su.se.bakover.domain.revurdering.iverksett.opphør.medUtbetaling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.simulering.kontrollsimuler
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.fromOpphør
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForOpphør
import no.nav.su.se.bakover.domain.sak.oppdaterRevurdering
import vedtak.domain.VedtakSomKanRevurderes
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import java.time.Clock

internal fun Sak.iverksettOpphørtRevurderingMedUtbetaling(
    revurdering: RevurderingTilAttestering.Opphørt,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil, IverksettOpphørtRevurderingMedUtbetalingResponse> {
    require(this.revurderinger.contains(revurdering))

    if (this.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg(revurdering, clock).isLeft()) {
        return KunneIkkeIverksetteRevurdering.Saksfeil.DetHarKommetNyeOverlappendeVedtak.left()
    }

    return revurdering.tilIverksatt(
        attestant = attestant,
        clock = clock,
    ).mapLeft {
        KunneIkkeIverksetteRevurdering.Saksfeil.Revurderingsfeil(it)
    }.flatMap { iverksattRevurdering ->
        kontrollsimuler(
            utbetalingForSimulering = this.lagUtbetalingForOpphør(
                opphørsperiode = revurdering.periode,
                behandler = attestant,
                clock = clock,
            ),
            simuler = simuler,
            saksbehandlersSimulering = revurdering.simulering,
        ).mapLeft {
            KunneIkkeIverksetteRevurdering.Saksfeil.KontrollsimuleringFeilet(it)
        }.map { simulertUtbetaling ->
            VedtakSomKanRevurderes.fromOpphør(
                revurdering = iverksattRevurdering,
                utbetalingId = simulertUtbetaling.id,
                clock = clock,
            ).let { vedtak ->
                IverksettOpphørtRevurderingMedUtbetalingResponse(
                    sak = oppdaterRevurdering(iverksattRevurdering)
                        .copy(
                            vedtakListe = vedtakListe.filterNot { it.id == vedtak.id } + vedtak,
                            // TODO jah: Her legger vi til en [SimulertUtbetaling] istedenfor en [OversendtUtbetaling] det kan i første omgang klusse til testdataene.
                            utbetalinger = utbetalinger.filterNot { it.id == simulertUtbetaling.id } + simulertUtbetaling,
                        ),
                    vedtak = vedtak,
                    utbetaling = simulertUtbetaling,
                )
            }
        }
    }
}
