package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.revurdering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.revurdering.SendTilAttesteringRequest
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeAktørId
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.manglerBeslutningPåForhåndsvarsel
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

@KtorExperimentalAPI
internal fun Route.sendRevurderingTilAttestering(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {

        data class Body(
            val fritekstTilBrev: String,
            val skalFøreTilBrevutsending: Boolean?,
        )

        post("$revurderingPath/{revurderingId}/tilAttestering") {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    revurderingService.sendTilAttestering(
                        SendTilAttesteringRequest(
                            revurderingId = revurderingId,
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                            fritekstTilBrev = body.fritekstTilBrev,
                            skalFøreTilBrevutsending = body.skalFøreTilBrevutsending ?: true,
                        ),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Sendt revurdering til attestering med id $revurderingId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                        },
                    )
                }
            }
        }
    }
}

internal fun KunneIkkeSendeRevurderingTilAttestering.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeSendeRevurderingTilAttestering.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
        is KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId -> fantIkkeAktørId
        is KunneIkkeSendeRevurderingTilAttestering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
        is KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør -> BadRequest.errorJson(
            "G-regulering kan ikke føre til opphør",
            "g_regulering_kan_ikke_føre_til_opphør",
        )
        is KunneIkkeSendeRevurderingTilAttestering.ManglerBeslutningPåForhåndsvarsel -> manglerBeslutningPåForhåndsvarsel
    }
}
