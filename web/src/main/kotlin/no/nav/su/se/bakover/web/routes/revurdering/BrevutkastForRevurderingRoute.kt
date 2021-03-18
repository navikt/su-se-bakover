package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeRevurdering
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
                revurderingService.hentBrevutkast(revurderingId).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = {
                        call.audit("Hentet brevutkast for revurdering med id $revurderingId")
                        call.respondBytes(it, ContentType.Application.Pdf)
                    },
                )
            }
        }
        post("$revurderingPath/{revurderingId}/brevutkast") {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    revurderingService.lagBrevutkast(revurderingId, body.fritekst).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.audit("Laget brevutkast for revurdering med id $revurderingId")
                            call.respondBytes(it, ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }
}

private fun KunneIkkeLageBrevutkastForRevurdering.tilResultat(): Resultat {
    return when (this) {
        is FantIkkeRevurdering -> fantIkkeRevurdering
        KunneIkkeLageBrevutkast -> InternalServerError.errorJson(
            "Kunne ikke lage brevutkast",
            "kunne_ikke_lage_brevutkast",
        )
        KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson -> InternalServerError.errorJson(
            "Fant ikke person",
            "fant_ikke_person",
        )
        KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> InternalServerError.errorJson(
            "Kunne ikke hente navn for saksbehandler eller attestant",
            "navneoppslag_feilet",
        )
    }
}
