package no.nav.su.se.bakover.domain.revurdering.iverksett.opphør

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.avkorting.oppdaterUteståendeAvkortingVedIverksettelse
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForOpphør
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock

internal fun Sak.iverksettOpphørtRevurdering(
    revurdering: RevurderingTilAttestering.Opphørt,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil, IverksettOpphørtRevurderingResponse> {
    require(this.revurderinger.contains(revurdering))

    if (avventerKravgrunnlag()) {
        return KunneIkkeIverksetteRevurdering.Saksfeil.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
    }

    if (this.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg(revurdering, clock).isLeft()) {
        return KunneIkkeIverksetteRevurdering.Saksfeil.DetHarKommetNyeOverlappendeVedtak.left()
    }

    return revurdering.tilIverksatt(
        attestant = attestant,
        uteståendeAvkortingPåSak = uteståendeAvkorting as? Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        clock = clock,
    ).mapLeft {
        KunneIkkeIverksetteRevurdering.Saksfeil.Revurderingsfeil(it)
    }.flatMap { iverksattRevurdering ->
        lagUtbetalingForOpphør(
            opphørsperiode = revurdering.opphørsperiodeForUtbetalinger,
            behandler = attestant,
            clock = clock,
        ).let {
            simulerUtbetaling(
                utbetalingForSimulering = it,
                periode = revurdering.opphørsperiodeForUtbetalinger,
                simuler = simuler,
                kontrollerMotTidligereSimulering = revurdering.simulering,
                clock = clock,
            )
        }.mapLeft {
            KunneIkkeIverksetteRevurdering.Saksfeil.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(it))
        }.map { simulertUtbetaling ->
            VedtakSomKanRevurderes.from(
                revurdering = iverksattRevurdering,
                utbetalingId = simulertUtbetaling.id,
                clock = clock,
            ).let { vedtak ->
                IverksettOpphørtRevurderingResponse(
                    sak = copy(
                        revurderinger = revurderinger.filterNot { it.id == revurdering.id } + iverksattRevurdering,
                        vedtakListe = vedtakListe.filterNot { it.id == vedtak.id } + vedtak,
                        // TODO jah: Her legger vi til en [SimulertUtbetaling] istedenfor en [OversendtUtbetaling] det kan i første omgang klusse til testdataene.
                        utbetalinger = utbetalinger.filterNot { it.id == simulertUtbetaling.id } + simulertUtbetaling,
                    ).oppdaterUteståendeAvkortingVedIverksettelse(
                        behandletAvkorting = vedtak.behandling.avkorting,
                    ),
                    vedtak = vedtak,
                    utbetaling = simulertUtbetaling,
                )
            }
        }
    }
}
