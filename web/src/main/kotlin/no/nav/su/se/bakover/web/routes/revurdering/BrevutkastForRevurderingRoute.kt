package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

@KtorExperimentalAPI
internal fun Route.brevutkastForRevurdering(
    revurderingService: RevurderingService
) {
    authorize(Brukerrolle.Saksbehandler) {

        data class Body(val fritekst: String)

        get("$revurderingPath/{revurderingId}/brevutkast") {
            call.withRevurderingId { revurderingId ->
                val revurdering = revurderingService.hentRevurdering(revurderingId)
                    ?: return@withRevurderingId call.svar(NotFound.errorJson("Fant ikke revurdering", "fant_ikke_revurdering"))

                revurderingService.hentBrevutkast(revurderingId).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = {
                        call.sikkerlogg("Hentet brevutkast for revurdering med id $revurderingId")
                        call.audit(revurdering.fnr, AuditLogEvent.Action.ACCESS, revurderingId)
                        call.respondBytes(it, ContentType.Application.Pdf)
                    },
                )
            }
        }
        post("$revurderingPath/{revurderingId}/brevutkast") {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    val revurdering = revurderingService.hentRevurdering(revurderingId)
                        ?: return@withRevurderingId call.svar(NotFound.errorJson("Fant ikke revurdering", "fant_ikke_revurdering"))

                    revurderingService.lagBrevutkast(revurderingId, body.fritekst).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Laget brevutkast for revurdering med id $revurderingId")
                            call.audit(revurdering.fnr, AuditLogEvent.Action.ACCESS, revurderingId)
                            call.respondBytes(it, ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }
}
