package no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel

import arrow.core.merge
import arrow.core.raise.either
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.revurdering.REVURDERING_PATH
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory

data class ForhåndsvarsleBody(val fritekst: String?)

internal fun Route.forhåndsvarslingRoute(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post("$REVURDERING_PATH/{revurderingId}/forhandsvarsel") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withValidertBody<ForhåndsvarsleBody>(log) { body ->
                call.withRevurderingId { revurderingId ->
                    either {
                        val revurdering = revurderingService.lagreOgSendForhåndsvarsel(
                            RevurderingId(revurderingId),
                            NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                            fritekst = body.fritekst,
                        ).mapLeft { kunneIkkeForhåndsvarsle -> kunneIkkeForhåndsvarsle.tilResultat() }.bind()

                        call.audit(revurdering.fnr, AuditLogEvent.Action.UPDATE, revurdering.id.value)
                        call.sikkerlogg("Forhåndsvarslet bruker med revurderingId $revurderingId")
                        Resultat.json(HttpStatusCode.OK, serialize(revurdering.toJson(formuegrenserFactory)))
                    }.merge().let { resultat -> call.svar(resultat) }
                }
            }
        }
    }

    data class ForhåndsvarselBrevutkastBody(val fritekst: String)
    post("$REVURDERING_PATH/{revurderingId}/brevutkastForForhandsvarsel") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withValidertBody<ForhåndsvarselBrevutkastBody>(log) { body ->
                    val revurdering =
                        revurderingService.hentRevurdering(RevurderingId(revurderingId)) ?: return@authorize call.svar(
                            fantIkkeRevurdering,
                        )

                    revurderingService.lagBrevutkastForForhåndsvarsling(RevurderingId(revurderingId), call.suUserContext.saksbehandler, body.fritekst).fold(
                        ifLeft = { kunneIkkeLageBrevutkast -> call.svar(kunneIkkeLageBrevutkast.tilResultat()) },
                        ifRight = { brevutkast ->
                            call.sikkerlogg("Laget brevutkast for forhåndsvarsel for revurdering med id $revurderingId")
                            call.audit(revurdering.fnr, AuditLogEvent.Action.ACCESS, revurderingId)
                            call.respondBytes(brevutkast.getContent(), ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }
}
