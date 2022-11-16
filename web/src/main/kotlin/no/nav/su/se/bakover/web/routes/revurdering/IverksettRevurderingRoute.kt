package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.avkortingErAlleredeAvkortet
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.lagringFeilet
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.sakAvventerKravgrunnlagForTilbakekreving
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.metrics.SuMetrics
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingService
import no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
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
    is KunneIkkeIverksetteRevurdering.FeilVedIverksettelse -> {
        when (val f = this.feil) {
            no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
            no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
            no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen -> avkortingErAlleredeAvkortet
            is no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale -> f.utbetalingFeilet.tilResultat()
            no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving -> sakAvventerKravgrunnlagForTilbakekreving
            is no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.UgyldigTilstand -> ugyldigTilstand(f.fra, f.til)
        }
    }
    is KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet -> {
        when (val f = this.feil) {
            is KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeUtbetale -> f.utbetalingFeilet.tilResultat()
            KunneIkkeFerdigstilleIverksettelsestransaksjon.LagringFeilet -> lagringFeilet
            KunneIkkeFerdigstilleIverksettelsestransaksjon.Opphør.KunneIkkeAnnullereKontrollsamtale -> HttpStatusCode.InternalServerError.errorJson(
                "Kunne ikke annulere kontrollsamtale",
                "kunne_ikke_annulere_kontrollsamtale",
            )
        }
    }
}
