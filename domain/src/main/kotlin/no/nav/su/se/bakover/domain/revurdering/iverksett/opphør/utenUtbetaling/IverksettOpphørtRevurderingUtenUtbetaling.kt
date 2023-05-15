package no.nav.su.se.bakover.domain.revurdering.iverksett.opphør.utenUtbetaling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.avkorting.oppdaterUteståendeAvkortingVedIverksettelse
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.opphør.kontrollsimuler
import no.nav.su.se.bakover.domain.revurdering.iverksett.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock

internal fun Sak.iverksettOpphørtRevurderingUtenUtbetaling(
    revurdering: RevurderingTilAttestering.Opphørt,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    lagDokument: (visitable: Visitable<LagBrevRequestVisitor>) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil, IverksettOpphørtRevurderingUtenUtbetalingResponse> {
    require(this.revurderinger.contains(revurdering))

    if (avventerKravgrunnlag()) {
        return KunneIkkeIverksetteRevurdering.Saksfeil.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
    }

    if (this.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg(revurdering, clock).isLeft()) {
        return KunneIkkeIverksetteRevurdering.Saksfeil.DetHarKommetNyeOverlappendeVedtak.left()
    }

    // Opphøret som fører til avkortingen baserer seg på en simulering av hele revurderingsperioen, hvor alle månedene inneholdt feilutbetalinger.
    // Vi ønsker å kontrollsimulere dette, selvom vi ikke skal lage noen utbetalinger.
    kontrollsimuler(
        attestant = attestant,
        clock = clock,
        simuler = simuler,
        periode = revurdering.periode,
        saksbehandlersSimulering = revurdering.simulering,
    ).onLeft { return it.left() }

    return revurdering.tilIverksatt(
        attestant = attestant,
        uteståendeAvkortingPåSak = uteståendeAvkorting as? Avkortingsvarsel.Utenlandsopphold.SkalAvkortes,
        clock = clock,
    ).mapLeft {
        KunneIkkeIverksetteRevurdering.Saksfeil.Revurderingsfeil(it)
    }.map { iverksattRevurdering ->

        VedtakSomKanRevurderes.from(
            revurdering = iverksattRevurdering,
            clock = clock,
        ).let { vedtak ->
            val dokument = lagDokument(vedtak)
                .getOrElse { return KunneIkkeIverksetteRevurdering.Saksfeil.KunneIkkeGenerereDokument(it).left() }
                .leggTilMetadata(
                    Dokument.Metadata(
                        sakId = vedtak.behandling.sakId,
                        søknadId = null,
                        vedtakId = vedtak.id,
                        revurderingId = null,
                    ),
                )
            IverksettOpphørtRevurderingUtenUtbetalingResponse(
                sak = copy(
                    revurderinger = revurderinger.filterNot { it.id == revurdering.id } + iverksattRevurdering,
                    vedtakListe = vedtakListe.filterNot { it.id == vedtak.id } + vedtak,
                ).oppdaterUteståendeAvkortingVedIverksettelse(
                    behandletAvkorting = vedtak.behandling.avkorting,
                ),
                vedtak = vedtak,
                dokument = dokument,
            )
        }
    }
}
