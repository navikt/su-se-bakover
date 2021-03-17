package no.nav.su.se.bakover.web.routes.behandling.søknadsbehandling

import arrow.core.Either
import arrow.core.extensions.either.applicative.applicative
import arrow.core.extensions.list.traverse.traverse
import arrow.core.fix
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Uføregrunnlag
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.søknadsbehandling.GrunnlagsdataService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.behandling.toJson
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody

const val grunnlagsdataPath = "$sakPath/{sakId}/behandlinger/{behandlingId}/grunnlagsdata"

@KtorExperimentalAPI
internal fun Route.grunnlagsdataRoute(
    grunnlagsdataService: GrunnlagsdataService,
    revurderingService: RevurderingService,
) {
    data class Body(
        val periode: PeriodeJson,
        val uføregrad: Int,
        val forventetInntekt: Int,
    ) {

        fun toDomain(): Either<Resultat, Uføregrunnlag> {
            val periode = periode.toPeriode().getOrHandle {
                return it.left()
            }
            val validUføregrad = Uføregrad.tryParse(uføregrad).getOrElse {
                return HttpStatusCode.BadRequest.errorJson(
                    message = "Uføregrad må være mellom en og hundre",
                    code = "uføregrad_må_være_mellom_en_og_hundre",
                ).left()
            }
            return Uføregrunnlag(
                periode = periode,
                uføregrad = validUføregrad,
                forventetInntekt = forventetInntekt
            ).right()
        }
    }

    fun List<Body>.toDomain(): Either<Resultat, List<Uføregrunnlag>> {
        return this.map {
            it.toDomain()
        }.traverse(Either.applicative(), ::identity).fix().map {
            it.fix()
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$grunnlagsdataPath/uføre") {
            call.withBehandlingId { behandlingId ->
                call.withBody<List<Body>> { body ->
                    val uføregrunnlagsjson = body.toDomain()
                    val resultat: Resultat = uføregrunnlagsjson.flatMap {
                        grunnlagsdataService.leggTilUføregrunnlag(behandlingId, it).mapLeft {
                            when (it) {
                                GrunnlagsdataService.KunneIkkeLeggeTilGrunnlagsdata.FantIkkeBehandling -> HttpStatusCode.NotFound.errorJson(
                                    "Fant ikke behandling",
                                    "fant_ikke_behandling"
                                )
                                GrunnlagsdataService.KunneIkkeLeggeTilGrunnlagsdata.UgyldigTilstand -> HttpStatusCode.BadRequest.errorJson(
                                    "Ugyldig tilstand",
                                    "ugyldig_tilstand"
                                )
                            }
                        }
                    }.map {
                        when (it) {
                            is Søknadsbehandling -> Resultat.json(Created, serialize(it.toJson()))
                            is Revurdering -> Resultat.json(Created, serialize(it.toJson(revurderingService)))
                            else -> HttpStatusCode.InternalServerError.errorJson("""Ukjent Behandlingstype""", "ukjent_behandlingstype")
                        }
                    }.getOrHandle { it }
                    call.svar(resultat)
                }
            }
        }
    }
}
