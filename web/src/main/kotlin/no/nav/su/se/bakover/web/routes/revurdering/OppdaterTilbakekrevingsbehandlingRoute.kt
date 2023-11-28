package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.tilbakekreving.KunneIkkeOppdatereTilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.revurdering.tilbakekreving.OppdaterTilbakekrevingsbehandlingRequest
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.oppdaterTilbakekrevingsbehandlingRoute(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    data class Body(
        val avgjørelse: OppdaterTilbakekrevingsbehandlingRequest.Avgjørelse,
    )
    post("$REVURDERING_PATH/{revurderingId}/tilbakekreving") {
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
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(formuegrenserFactory))))
                        },
                    )
                }
            }
        }
    }
}

internal fun KunneIkkeOppdatereTilbakekrevingsbehandling.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeOppdatereTilbakekrevingsbehandling.UgyldigTilstand -> {
            HttpStatusCode.BadRequest.errorJson(
                "Ugyldig tilstand for oppdatering av tilbakekrevingsbehandling",
                "oppdater_tilbakekrevingsbehandling_ugyldig_tilstand",
            )
        }
    }
}
