package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.FantIkkeRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulere
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering.UgyldigTilstand
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withRevurderingId

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
                        call.sikkerlogg("Iverksatt revurdering med id $revurderingId")
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
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
    is KunneIkkeKontrollsimulere -> InternalServerError.errorJson(
        "Kunne ikke utføre kontrollsimulering",
        "kunne_ikke_kontrollsimulere",
    )
    KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulereFinnerIkkeKjøreplansperiodeForFom -> InternalServerError.errorJson(
        "Kontrollsimulering feilet: Finner ikke kjøreplansperiode for fom-dato",
        "kontrollsimulering_finner_ikke_kjøreplansperiode_for_fom",
    )
    KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulereFinnerIkkePerson -> InternalServerError.errorJson(
        "Kontrollsimulering feilet: Finner ikke person i TPS",
        "kontrollsimulering_finner_ikke_person_i_tps",
    )
    KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulereOppdragErStengtEllerNede -> InternalServerError.errorJson(
        "Kontrollsimulering feilet: Oppdrag er stengt eller nede",
        "kontrollsimulering_oppdrag_er_stengt_eller_nede",
    )
    is KunneIkkeUtbetale -> InternalServerError.errorJson(
        "Kunne ikke utføre utbetaling",
        "kunne_ikke_utbetale",
    )
    is KunneIkkeIverksetteRevurdering.KunneIkkeDistribuereBrev -> InternalServerError.errorJson(
        "Kunne ikke distribuere brev",
        "kunne_ikke_distribuere_brev",
    )
    is KunneIkkeIverksetteRevurdering.KunneIkkeJournaleføreBrev -> InternalServerError.errorJson(
        "Kunne ikke journalføre brev",
        "kunne_ikke_journalføre_brev",
    )
}
