package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.revurdering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeAktørId
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigPeriode
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withRevurderingId

@KtorExperimentalAPI
internal fun Route.sendRevurderingTilAttestering(
    revurderingService: RevurderingService
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/tilAttestering") {
            call.withRevurderingId { revurderingId ->
                revurderingService.sendTilAttestering(
                    revurderingId = revurderingId,
                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent)
                ).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = {
                        call.audit("Sendt revurdering til attestering med id $revurderingId")
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(revurderingService))))
                    },
                )
            }
        }
    }
}

private fun KunneIkkeSendeRevurderingTilAttestering.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeSendeRevurderingTilAttestering.UgyldigPeriode -> ugyldigPeriode(this.subError)
        is KunneIkkeSendeRevurderingTilAttestering.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
        is KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId -> fantIkkeAktørId
        is KunneIkkeSendeRevurderingTilAttestering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
    }
}
