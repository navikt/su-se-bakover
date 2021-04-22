package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.revurdering.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.sikkerlogg
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
                        call.sikkerlogg("Forhåndsvarslet bruker med revurderingId $revurderingId")
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                    }.mapLeft {
                        call.svar(it.tilResultat())
                    }
                }
            }
        }
    }
}

private fun KunneIkkeForhåndsvarsle.tilResultat() = when (this) {
    is KunneIkkeForhåndsvarsle.AlleredeForhåndsvarslet -> HttpStatusCode.Conflict.errorJson(
        "Allerede forhåndsvarslet",
        "allerede_forhåndsvarslet",
    )
    is KunneIkkeForhåndsvarsle.FantIkkeAktørId -> GenerelleRevurderingsfeilresponser.fantIkkeAktørId
    is KunneIkkeForhåndsvarsle.FantIkkePerson -> GenerelleRevurderingsfeilresponser.fantIkkePerson
    is KunneIkkeForhåndsvarsle.KunneIkkeDistribuere -> HttpStatusCode.InternalServerError.errorJson(
        "Kunne ikke distribuere brev",
        "kunne_ikke_distribuere_brev",
    )
    is KunneIkkeForhåndsvarsle.KunneIkkeJournalføre -> HttpStatusCode.InternalServerError.errorJson(
        "Kunne ikke journalføre brev",
        "kunne_ikke_journalføre_brev",
    )
    is KunneIkkeForhåndsvarsle.KunneIkkeOppretteOppgave -> GenerelleRevurderingsfeilresponser.kunneIkkeOppretteOppgave
    is KunneIkkeForhåndsvarsle.FantIkkeRevurdering -> GenerelleRevurderingsfeilresponser.fantIkkeRevurdering
    is KunneIkkeForhåndsvarsle.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
}
