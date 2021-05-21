package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.service.revurdering.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson.Companion.toFradrag
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId

@KtorExperimentalAPI
internal fun Route.leggTilFradragRevurdering(
    revurderingService: RevurderingService,

) {
    data class BeregningForRevurderingBody(
        val fradrag: List<FradragJson>,
    ) {
        fun toDomain(): Either<Resultat, List<Grunnlag.Fradragsgrunnlag>> =
            fradrag.toFradrag().map { it.map { fradrag -> Grunnlag.Fradragsgrunnlag(fradrag = fradrag) } }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/fradrag") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<BeregningForRevurderingBody> { body ->
                        call.svar(
                            body.toDomain().flatMap { fradrag ->
                                revurderingService.leggTilFradragsgrunnlag(
                                    LeggTilFradragsgrunnlagRequest(
                                        revurderingId,
                                        fradrag,
                                    ),
                                ).mapLeft {
                                    when (it) {
                                        KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling -> Revurderingsfeilresponser.fantIkkeRevurdering
                                        KunneIkkeLeggeTilFradragsgrunnlag.UgyldigStatus -> InternalServerError.errorJson(
                                            "ugyldig status for å legge til",
                                            "ugyldig_status_for_å_legge_til",
                                        )
                                    }
                                }.map {
                                    call.sikkerlogg("Lagret fradrag for revudering $revurderingId på $sakId")
                                    Resultat.json(
                                        HttpStatusCode.OK,
                                        serialize(it.revurdering.toJson()),
                                    )
                                }
                            }.getOrHandle { it },
                        )
                    }
                }
            }
        }
    }
}
