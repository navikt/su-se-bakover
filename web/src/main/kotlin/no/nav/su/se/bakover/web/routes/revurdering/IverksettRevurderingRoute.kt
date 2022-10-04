package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.metrics.SuMetrics
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.FantIkkeRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.UgyldigTilstand
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.web.routes.Feilresponser.avkortingErAlleredeAvkortet
import no.nav.su.se.bakover.web.routes.Feilresponser.ingenEndringUgyldig
import no.nav.su.se.bakover.web.routes.Feilresponser.lagringFeilet
import no.nav.su.se.bakover.web.routes.Feilresponser.sakAvventerKravgrunnlagForTilbakekreving
import no.nav.su.se.bakover.web.routes.Feilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withRevurderingId

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
    is FantIkkeRevurdering -> fantIkkeRevurdering
    is UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
    is AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
    is KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale -> this.utbetalingFeilet.tilResultat()
    KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen -> avkortingErAlleredeAvkortet
    KunneIkkeIverksetteRevurdering.IngenEndringErIkkeGyldig -> ingenEndringUgyldig
    KunneIkkeIverksetteRevurdering.LagringFeilet -> lagringFeilet
    KunneIkkeIverksetteRevurdering.KunneIkkeAnnulereKontrollsamtale -> HttpStatusCode.InternalServerError.errorJson(
        "Kunne ikke annulere kontrollsamtale",
        "kunne_ikke_annulere_kontrollsamtale",
    )

    KunneIkkeIverksetteRevurdering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving -> sakAvventerKravgrunnlagForTilbakekreving
}
