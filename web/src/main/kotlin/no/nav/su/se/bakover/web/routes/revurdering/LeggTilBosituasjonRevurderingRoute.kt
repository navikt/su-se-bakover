package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.service.revurdering.LeggTilBosituasjongrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId

internal fun Route.LeggTilBosituasjonRevurderingRoute(
    revurderingService: RevurderingService,
) {
    data class bosituasjonBody(
        val epsFnr: String?,
        val delerBolig: Boolean?,
        val ektemakeEllerSamboerUførFlyktning: Boolean?,
    )

    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/bosituasjongrunnlag") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<bosituasjonBody> { body ->
                        revurderingService.leggTilBosituasjongrunnlag(
                            LeggTilBosituasjongrunnlagRequest(
                                revurderingId,
                                body.epsFnr,
                                body.delerBolig,
                                body.ektemakeEllerSamboerUførFlyktning,
                            ),
                        ).map {
                            call.sikkerlogg("Lagret bosituasjon for revudering $revurderingId på $sakId")
                            Resultat.json(
                                HttpStatusCode.OK,
                                serialize(it.revurdering.toJson()),
                            )
                        }.mapLeft {
                            when (it) {
                                KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling -> Revurderingsfeilresponser.fantIkkeRevurdering
                                KunneIkkeLeggeTilBosituasjongrunnlag.EpsAlderErNull -> HttpStatusCode.InternalServerError.errorJson(
                                    "eps alder er null",
                                    "eps_alder_er_null",
                                )
                                KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeSlåOppEPS -> HttpStatusCode.InternalServerError.errorJson(
                                    "kunne ikke slå opp EPS",
                                    "kunne_ikke_slå_opp_eps",
                                )
                                KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData -> HttpStatusCode.BadRequest.errorJson(
                                    "ugyldig data",
                                    "ugyldig_Data",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
