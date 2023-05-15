package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.avkortingsfeil
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.lagringFeilet
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.sakAvventerKravgrunnlagForTilbakekreving
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.metrics.SuMetrics
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.sak.tilResultat
import no.nav.su.se.bakover.web.routes.tilResultat

internal fun Route.iverksettRevurderingRoute(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("$revurderingPath/{revurderingId}/iverksett") {
        authorize(Brukerrolle.Attestant) {
            call.withRevurderingId { revurderingId ->
                revurderingService.iverksett(
                    revurderingId = revurderingId,
                    attestant = NavIdentBruker.Attestant(call.suUserContext.navIdent),
                ).fold(
                    ifLeft = {
                        val message = it.tilResultat()
                        call.svar(message)
                    },
                    ifRight = {
                        call.sikkerlogg("Iverksatt revurdering med id $revurderingId")
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                        SuMetrics.vedtakIverksatt(SuMetrics.Behandlingstype.REVURDERING)
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory))))
                    },
                )
            }
        }
    }
}

private fun KunneIkkeIverksetteRevurdering.tilResultat() = when (this) {
    is KunneIkkeIverksetteRevurdering.Saksfeil -> {
        when (this) {
            is KunneIkkeIverksetteRevurdering.Saksfeil.FantIkkeRevurdering -> fantIkkeRevurdering
            is KunneIkkeIverksetteRevurdering.Saksfeil.KunneIkkeUtbetale -> utbetalingFeilet.tilResultat()
            is KunneIkkeIverksetteRevurdering.Saksfeil.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving -> sakAvventerKravgrunnlagForTilbakekreving
            is KunneIkkeIverksetteRevurdering.Saksfeil.UgyldigTilstand -> ugyldigTilstand(fra, til)
            is KunneIkkeIverksetteRevurdering.Saksfeil.Revurderingsfeil -> underliggende.tilResultat()
            is KunneIkkeIverksetteRevurdering.Saksfeil.DetHarKommetNyeOverlappendeVedtak -> Feilresponser.detHarKommetNyeOverlappendeVedtak
            is KunneIkkeIverksetteRevurdering.Saksfeil.KontrollsimuleringFeilet -> this.feil.tilResultat()
            is KunneIkkeIverksetteRevurdering.Saksfeil.KunneIkkeGenerereDokument -> this.feil.tilResultat()
        }
    }
    is KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet -> {
        when (val f = this.feil) {
            is KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeUtbetale -> f.utbetalingFeilet.tilResultat()
            is KunneIkkeFerdigstilleIverksettelsestransaksjon.UkjentFeil -> lagringFeilet
        }
    }
}

private fun RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.tilResultat() = when (this) {
    is RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson

    is RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.Avkortingsfeil -> avkortingsfeil
}
