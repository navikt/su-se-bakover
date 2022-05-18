package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppdatereTilbakekrevingsbehandling
import no.nav.su.se.bakover.service.revurdering.OppdaterTilbakekrevingsbehandlingRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

internal fun Route.oppdaterTilbakekrevingsbehandlingRoute(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {

    data class Body(
        val avgjørelse: OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse,
    )
    post("$revurderingPath/{revurderingId}/tilbakekreving") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    revurderingService.oppdaterTilbakekrevingsbehandling(
                        OppdaterTilbakekrevingsbehandlingRequest(
                            revurderingId = revurderingId,
                            avgjørelse = body.avgjørelse,
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                        ),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Oppdatert tilbakekrevingsbehandling for $revurderingId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.OK,
                                    serialize(it.toJson(satsFactory))
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

internal fun KunneIkkeOppdatereTilbakekrevingsbehandling.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeOppdatereTilbakekrevingsbehandling.FantIkkeRevurdering -> {
            Revurderingsfeilresponser.fantIkkeRevurdering
        }
        is KunneIkkeOppdatereTilbakekrevingsbehandling.UgyldigTilstand -> {
            HttpStatusCode.BadRequest.errorJson(
                "Ugyldig tilstand for oppdatering av tilbakekrevingsbehandling",
                "oppdater_tilbakekrevingsbehandling_ugyldig_tilstand",
            )
        }
    }
}
