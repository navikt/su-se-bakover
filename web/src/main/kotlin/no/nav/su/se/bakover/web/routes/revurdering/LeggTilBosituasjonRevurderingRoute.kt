package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.service.revurdering.LeggTilBosituasjongrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.ugyldigBody
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId

internal fun Route.LeggTilBosituasjonRevurderingRoute(
    revurderingService: RevurderingService,
) {
    data class BosituasjonBody(
        val epsFnr: String?,
        val delerBolig: Boolean?,
        val erEPSUførFlyktning: Boolean?,
        val begrunnelse: String?
    )

    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/bosituasjongrunnlag") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<BosituasjonBody> { body ->
                        revurderingService.leggTilBosituasjongrunnlag(
                            LeggTilBosituasjongrunnlagRequest(
                                revurderingId,
                                body.epsFnr,
                                body.delerBolig,
                                body.erEPSUførFlyktning,
                                body.begrunnelse,
                            ),
                        ).map {
                            call.sikkerlogg("Lagret bosituasjon for revudering $revurderingId på $sakId")
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.OK,
                                    serialize(it.toJson()),
                                ),
                            )
                        }.mapLeft {
                            call.svar(it.tilResultat())
                        }
                    }
                }
            }
        }
    }
}

private fun KunneIkkeLeggeTilBosituasjongrunnlag.tilResultat() = when (this) {
    KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling -> Revurderingsfeilresponser.fantIkkeRevurdering
    KunneIkkeLeggeTilBosituasjongrunnlag.EpsAlderErNull -> HttpStatusCode.InternalServerError.errorJson(
        "eps alder er null",
        "eps_alder_er_null",
    )
    KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeSlåOppEPS -> HttpStatusCode.InternalServerError.errorJson(
        "kunne ikke slå opp EPS",
        "kunne_ikke_slå_opp_eps",
    )
    KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData -> ugyldigBody
    is KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
        this.fra,
        this.til,
    )
    is KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeEndreBosituasjongrunnlag -> Feilresponser.kunneIkkeLeggeTilBosituasjonsgrunnlag
}
