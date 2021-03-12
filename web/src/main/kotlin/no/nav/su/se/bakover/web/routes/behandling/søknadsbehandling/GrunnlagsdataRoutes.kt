package no.nav.su.se.bakover.web.routes.behandling.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.BehandlingUføregrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.Uføregrad
import no.nav.su.se.bakover.service.søknadsbehandling.GrunnlagsdataService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId

const val grunnlagsdataPath = "$sakPath/{sakId}/behandlinger/{behandlingId}/grunnlagsdata"

@KtorExperimentalAPI
internal fun Route.grunnlagsdataRoute(
    grunnlagsdataService: GrunnlagsdataService
) {
    data class Body(
        val periode: PeriodeJson,
        val uføregrad: Int,
        val forventetInntekt: Int,
    ) {

        fun toDomain(): Either<Resultat, BehandlingUføregrunnlag> {
            val periode = periode.toPeriode().getOrHandle { return it.left() }
            val validUføregrad = Uføregrad.tryParse(uføregrad).getOrElse {
                return HttpStatusCode.BadRequest.errorJson(
                    message = "Uføregrad må være mellom en og hundre",
                    code = "uføregrad_må_være_mellom_en_og_hundre",
                ).left()
            }
            return BehandlingUføregrunnlag(
                periode = periode,
                uføregrad = validUføregrad,
                forventetInntekt = forventetInntekt
            ).right()
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$grunnlagsdataPath/uføre") {
            call.withSakId { sakId ->
                call.withBehandlingId { behandlingId ->
                    call.withBody<Body> { body ->
                        body.toDomain().map {
                            grunnlagsdataService.leggTilUførerunnlag(sakId, behandlingId, listOf(it))
                        }
                    }
                }
            }
        }
    }
}
