package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

@KtorExperimentalAPI
internal fun Route.forhåndsvarslingRoute(
    revurderingService: RevurderingService,
) {
    data class Body(val fritekst: String)

    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/forhandsvarsle") {
            call.withBody<Body> { body ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.forhåndsvarsle(
                        revurderingId,
                        NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                        fritekst = body.fritekst,
                    ).map {
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                    }
                }
            }
        }
    }
}
