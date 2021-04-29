package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.revurdering.Revurderingshandling
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

@KtorExperimentalAPI
internal fun Route.forhåndsvarslingRoute(
    revurderingService: RevurderingService,
) {

    data class Body(val revurderingshandling: Revurderingshandling, val fritekst: String)
    data class ForhåndsvarselBrevutkastBody(val fritekst: String)

    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/forhandsvarsleEllerSendTilAttestering") {
            call.withBody<Body> { body ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.forhåndsvarsleEllerSendTilAttestering(
                        revurderingId,
                        NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                        body.revurderingshandling,
                        fritekst = body.fritekst,
                    ).map {
                        call.sikkerlogg("Forhåndsvarslet bruker med revurderingId $revurderingId")
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                    }.mapLeft {
                        call.svar(it.tilResultat())
                    }
                }
            }
        }

        post("$revurderingPath/{revurderingId}/brevutkastForForhandsvarsel") {
            call.withRevurderingId { revurderingId ->
                call.withBody<ForhåndsvarselBrevutkastBody> { body ->
                    val revurdering = revurderingService.hentRevurdering(revurderingId)
                        ?: return@withRevurderingId call.svar(HttpStatusCode.NotFound.errorJson("Fant ikke revurdering", "fant_ikke_revurdering"))

                    revurderingService.lagBrevutkastForForhåndsvarsling(revurderingId, body.fritekst).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Laget brevutkast for forhåndsvarsel for revurdering med id $revurderingId")
                            call.audit(revurdering.fnr, AuditLogEvent.Action.ACCESS, revurderingId)
                            call.respondBytes(it, ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }
}
