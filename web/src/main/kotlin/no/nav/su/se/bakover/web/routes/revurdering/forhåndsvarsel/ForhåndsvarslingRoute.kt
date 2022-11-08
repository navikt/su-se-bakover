package no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.RevurderingService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.revurderingPath
import no.nav.su.se.bakover.web.routes.revurdering.toJson

internal fun Route.forhåndsvarslingRoute(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    data class ForhåndsvarsleBody(val fritekst: String)
    post("$revurderingPath/{revurderingId}/forhandsvarsel") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBody<ForhåndsvarsleBody> { body ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.lagreOgSendForhåndsvarsel(
                        revurderingId,
                        NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                        fritekst = body.fritekst,
                    ).map {
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                        call.sikkerlogg("Forhåndsvarslet bruker med revurderingId $revurderingId")
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory))))
                    }.mapLeft {
                        call.svar(it.tilResultat())
                    }
                }
            }
        }
    }

    data class ForhåndsvarselBrevutkastBody(val fritekst: String)
    post("$revurderingPath/{revurderingId}/brevutkastForForhandsvarsel") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<ForhåndsvarselBrevutkastBody> { body ->
                    val revurdering =
                        revurderingService.hentRevurdering(revurderingId) ?: return@withRevurderingId call.svar(
                            fantIkkeRevurdering,
                        )

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
