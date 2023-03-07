package no.nav.su.se.bakover.domain.revurdering.iverksett.innvilg

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.avkorting.oppdaterUteståendeAvkortingVedIverksettelse
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock

internal fun Sak.iverksettInnvilgetRevurdering(
    revurdering: RevurderingTilAttestering.Innvilget,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil, IverksettInnvilgetRevurderingResponse> {
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
        lagNyUtbetaling(
            saksbehandler = attestant,
            beregning = iverksattRevurdering.beregning,
            clock = clock,
            utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            uføregrunnlag = when (iverksattRevurdering.sakstype) {
                Sakstype.ALDER -> {
                    null
                }

                Sakstype.UFØRE -> {
                    iverksattRevurdering.vilkårsvurderinger.uføreVilkår()
                        .getOrElse { throw IllegalStateException("Revurdering uføre: ${iverksattRevurdering.id} mangler uføregrunnlag") }
                        .grunnlag
                        .toNonEmptyList()
                }
            },
        ).let {
            simulerUtbetaling(
                utbetalingForSimulering = it,
                periode = iverksattRevurdering.periode,
                simuler = simuler,
                kontrollerMotTidligereSimulering = iverksattRevurdering.simulering,
            )
        }.mapLeft { feil ->
            KunneIkkeIverksetteRevurdering.Saksfeil.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(feil))
        }.map { simulertUtbetaling ->
            VedtakSomKanRevurderes.from(
                revurdering = iverksattRevurdering,
                utbetalingId = simulertUtbetaling.id,
                clock = clock,
            ).let { vedtak ->
                IverksettInnvilgetRevurderingResponse(
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
