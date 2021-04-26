package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.grunnlag.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

private data class Body(
    val periode: PeriodeJson,
    val uføregrad: Int,
    val forventetInntekt: Int,
) {

    fun toDomain(): Either<Resultat, Grunnlag.Uføregrunnlag> {
        val periode = periode.toPeriode().getOrHandle {
            return it.left()
        }
        val validUføregrad = Uføregrad.tryParse(uføregrad).getOrElse {
            return HttpStatusCode.BadRequest.errorJson(
                message = "Uføregrad må være mellom en og hundre",
                code = "uføregrad_må_være_mellom_en_og_hundre",
            ).left()
        }
        return Grunnlag.Uføregrunnlag(
            periode = periode,
            uføregrad = validUføregrad,
            forventetInntekt = forventetInntekt,
        ).right()
    }
}

@KtorExperimentalAPI
internal fun Route.leggTilGrunnlagRevurderingRoutes(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/uføregrunnlag") {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    call.svar(
                        body.toDomain()
                            .flatMap { uføregrunnlag ->
                                revurderingService.leggTilUføregrunnlag(
                                    revurderingId = revurderingId,
                                    uføregrunnlag = listOf(uføregrunnlag),
                                ).mapLeft {
                                    when (it) {
                                        KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling -> HttpStatusCode.NotFound.errorJson(
                                            "fant ikke behandling",
                                            "fant_ikke_behandling",
                                        )
                                        KunneIkkeLeggeTilGrunnlag.UgyldigStatus -> InternalServerError.errorJson(
                                            "ugyldig status for å legge til",
                                            "ugyldig_status_for_å_legge_til",
                                        )
                                    }
                                }.map {
                                    Resultat.json(HttpStatusCode.Created, serialize(it.toJson()))
                                }
                            }.getOrHandle {
                                it
                            },
                    )
                }
            }
        }
    }
}
