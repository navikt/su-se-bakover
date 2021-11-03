package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIKkeLageBrevutkastForAvsluttingAvRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

internal fun Route.AvsluttRevurderingRoute(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {

        data class AvsluttRevurderingBody(
            val begrunnelse: String,
            val fritekst: String?,
        )

        post("$revurderingPath/{revurderingId}/avslutt") {
            call.withBody<AvsluttRevurderingBody> { body ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.avsluttRevurdering(
                        revurderingId = revurderingId,
                        begrunnelse = body.begrunnelse,
                        fritekst = body.fritekst,
                    ).fold(
                        ifLeft = {
                            when (it) {
                                KunneIkkeAvslutteRevurdering.FantIkkeRevurdering -> call.svar(Revurderingsfeilresponser.fantIkkeRevurdering)
                                is KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetRevurdering -> call.svar(
                                    HttpStatusCode.BadRequest.errorJson(
                                        "Revurderingen er allerede avsluttet",
                                        "revurderingen_er_allerede_avsluttet",
                                    ),
                                )
                                KunneIkkeAvslutteRevurdering.KunneIkkeLageDokument -> call.svar(Feilresponser.Brev.kunneIkkeLageBrevutkast)
                            }
                        },
                        ifRight = {
                            call.sikkerlogg("Avsluttet behandling av revurdering med revurderingId $revurderingId")
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                        },
                    )
                }
            }
        }

        data class BrevutkastForAvslutting(
            val fritekst: String?,
        )

        post("$revurderingPath/{revurderingId}/brevutkastForAvslutning") {
            call.withRevurderingId { revurderingId ->
                call.withBody<BrevutkastForAvslutting> { body ->
                    revurderingService.brevutkastForAvslutting(revurderingId, body.fritekst).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Laget brevutkast for revurdering med id $revurderingId")
                            call.audit(it.first, AuditLogEvent.Action.ACCESS, revurderingId)
                            call.respondBytes(it.second, ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }
}

private fun KunneIKkeLageBrevutkastForAvsluttingAvRevurdering.tilResultat(): Resultat {
    return when (this) {
        KunneIKkeLageBrevutkastForAvsluttingAvRevurdering.FantIkkeRevurdering -> Revurderingsfeilresponser.fantIkkeRevurdering
        KunneIKkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeLageBrevutkast -> Feilresponser.Brev.kunneIkkeLageBrevutkast
        KunneIKkeLageBrevutkastForAvsluttingAvRevurdering.RevurderingenErIkkeForhåndsvarslet -> HttpStatusCode.BadRequest.errorJson(
            "Revurderingen er ikke forhåndsvarslet for å vise brev",
            "revurdering_er_ikke_forhåndsvarslet_for_å_vise_brev",
        )
    }
}
