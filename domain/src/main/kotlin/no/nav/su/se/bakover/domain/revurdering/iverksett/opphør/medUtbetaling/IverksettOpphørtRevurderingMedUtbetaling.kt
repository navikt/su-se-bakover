package no.nav.su.se.bakover.domain.revurdering.iverksett.opphør.medUtbetaling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.opphør.kontrollsimuler
import no.nav.su.se.bakover.domain.revurdering.iverksett.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg
import no.nav.su.se.bakover.domain.sak.oppdaterRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock

internal fun Sak.iverksettOpphørtRevurderingMedUtbetaling(
    revurdering: RevurderingTilAttestering.Opphørt,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil, IverksettOpphørtRevurderingMedUtbetalingResponse> {
    require(this.revurderinger.contains(revurdering))

    if (avventerKravgrunnlag()) {
        return KunneIkkeIverksetteRevurdering.Saksfeil.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
    }

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
            attestant = attestant,
            clock = clock,
            simuler = simuler,
            periode = revurdering.periode,
            saksbehandlersSimulering = revurdering.simulering,
        ).map { simulertUtbetaling ->
            VedtakSomKanRevurderes.from(
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
