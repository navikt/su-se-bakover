package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.FantIkkeRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulere
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.UgyldigTilstand
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withRevurderingId

@KtorExperimentalAPI
internal fun Route.iverksettRevurderingRoute(
    revurderingService: RevurderingService
) {
    authorize(Brukerrolle.Attestant) {
        post("$revurderingPath/{revurderingId}/iverksett") {
            call.withRevurderingId { revurderingId ->
                revurderingService.iverksett(
                    revurderingId = revurderingId, attestant = NavIdentBruker.Attestant(call.suUserContext.navIdent)
                ).fold(
                    ifLeft = {
                        val message = it.tilResultat()
                        call.svar(message)
                    },
                    ifRight = {
                        call.audit("Iverksatt revurdering med id $revurderingId")
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                    },
                )
            }
        }
    }
}

private fun KunneIkkeIverksetteRevurdering.tilResultat() = when (this) {
    is FantIkkeRevurdering -> fantIkkeRevurdering
    is UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
    is AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> Forbidden.errorJson(
        "Attestant og saksbehandler kan ikke være samme person",
        "attestant_og_saksbehandler_kan_ikke_være_samme_person",
    )
    is KunneIkkeJournalføreBrev -> InternalServerError.errorJson(
        "Feil ved journalføring av vedtaksbrev",
        "kunne_ikke_journalføre_brev",
    )
    is KunneIkkeKontrollsimulere -> InternalServerError.errorJson(
        "Kunne ikke utføre kontrollsimulering",
        "kunne_ikke_kontrollsimulere",
    )
    is KunneIkkeUtbetale -> InternalServerError.errorJson(
        "Kunne ikke utføre utbetaling",
        "kunne_ikke_utbetale",
    )
    is SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> InternalServerError.errorJson(
        "Oppdaget inkonsistens mellom tidligere utført simulering og kontrollsimulering. Ny simulering må utføres og kontrolleres før iverksetting kan gjennomføres",
        "simulering_har_blitt_endret_siden_saksbehandler_simulerte",
    )
}
