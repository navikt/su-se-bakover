package no.nav.su.se.bakover.web.routes.søknadsbehandling

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
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagBody
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody

@KtorExperimentalAPI
internal fun Route.leggTilGrunnlagSøknadsbehandlingRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/uføregrunnlag") {
            call.withBehandlingId { behandlingId ->
                call.withBody<UføregrunnlagBody> { body ->
                    call.svar(
                        body.toDomain()
                            .flatMap { uføregrunnlag ->
                                søknadsbehandlingService.leggTilUføregrunnlag(
                                    SøknadsbehandlingService.LeggTilUføregrunnlagRequest(
                                        behandlingId = behandlingId,
                                        uføregrunnlag = uføregrunnlag,
                                        oppfylt = body.oppfylt,
                                        begrunnelse = body.begrunnelse,
                                    ),
                                ).mapLeft {
                                    when (it) {
                                        SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling -> HttpStatusCode.NotFound.errorJson("fant ikke behandling", "fant_ikke_behandling")
                                        SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.UgyldigStatus -> InternalServerError.errorJson("ugyldig status for å legge til", "ugyldig_status_for_å_legge_til")
                                        SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.KunneIkkeVilkårsvurdere -> InternalServerError.errorJson("ugyldig status for å legge til", "ugyldig_status_for_å_legge_til")
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
