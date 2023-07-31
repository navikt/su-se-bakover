package no.nav.su.se.bakover.domain.revurdering.iverksett.opphør.utenUtbetaling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.avkorting.oppdaterUteståendeAvkortingVedIverksettelse
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.brev.lagDokumentKommando
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.iverksett.opphør.kontrollsimuler
import no.nav.su.se.bakover.domain.revurdering.iverksett.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg
import no.nav.su.se.bakover.domain.sak.oppdaterRevurdering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørAvkorting
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock

internal fun Sak.iverksettOpphørtRevurderingUtenUtbetaling(
    revurdering: RevurderingTilAttestering.Opphørt,
    attestant: NavIdentBruker.Attestant,
    clock: Clock,
    satsFactory: SatsFactory,
    simuler: (utbetaling: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    lagDokument: (command: IverksettRevurderingDokumentCommand) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
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
            val maybeDokument = genererDokumentHvisViSkal(vedtak, satsFactory, clock, lagDokument).getOrElse {
                return it.left()
            }
            IverksettOpphørtRevurderingUtenUtbetalingResponse(
                sak = oppdaterRevurdering(iverksattRevurdering)
                    .copy(
                        vedtakListe = vedtakListe.filterNot { it.id == vedtak.id } + vedtak,
                    ).oppdaterUteståendeAvkortingVedIverksettelse(
                        behandletAvkorting = vedtak.behandling.avkorting,
                    ),
                vedtak = vedtak,
                dokument = maybeDokument,
            )
        }
    }
}

private fun genererDokumentHvisViSkal(
    vedtak: VedtakOpphørAvkorting,
    satsFactory: SatsFactory,
    clock: Clock,
    lagDokument: (command: IverksettRevurderingDokumentCommand) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
): Either<KunneIkkeIverksetteRevurdering.Saksfeil.KunneIkkeGenerereDokument, Dokument.MedMetadata?> {
    if (!vedtak.skalGenerereDokumentVedFerdigstillelse()) {
        return null.right()
    }
    val pdfCommand = vedtak.behandling.lagDokumentKommando(satsFactory = satsFactory, clock = clock) as IverksettRevurderingDokumentCommand
    return lagDokument(pdfCommand)
        .mapLeft { KunneIkkeIverksetteRevurdering.Saksfeil.KunneIkkeGenerereDokument(it) }
        .map {
            it.leggTilMetadata(
                Dokument.Metadata(
                    sakId = vedtak.behandling.sakId,
                    søknadId = null,
                    vedtakId = vedtak.id,
                    revurderingId = null,
                ),
            )
        }
}
