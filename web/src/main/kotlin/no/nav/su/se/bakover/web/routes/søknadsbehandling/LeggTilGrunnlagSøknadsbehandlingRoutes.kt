package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import java.util.UUID

@KtorExperimentalAPI
internal fun Route.leggTilGrunnlagSøknadsbehandlingRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    data class UføregrunnlagBody(
        val periode: PeriodeJson,
        val uføregrad: Int?,
        val forventetInntekt: Int?,
        val resultat: Behandlingsinformasjon.Uførhet.Status,
        val begrunnelse: String,
    ) {

        fun toLeggTilUføregrunnlagRequest(behandlingId: UUID): Either<Resultat, LeggTilUførevurderingRequest> {
            return LeggTilUførevurderingRequest(
                behandlingId = behandlingId,
                periode = periode.toPeriode().getOrHandle {
                    return it.left()
                },
                uføregrad = uføregrad?.let {
                    Uføregrad.tryParse(it).getOrHandle {
                        return HttpStatusCode.BadRequest.errorJson(
                            message = "Uføregrad må være mellom en og hundre",
                            code = "uføregrad_må_være_mellom_en_og_hundre",
                        ).left()
                    }
                },
                forventetInntekt = forventetInntekt,
                oppfylt = resultat,
                begrunnelse = begrunnelse,
            ).right()
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/uføregrunnlag") {
            call.withBehandlingId { behandlingId ->
                call.withBody<UføregrunnlagBody> { body ->
                    call.svar(
                        body.toLeggTilUføregrunnlagRequest(behandlingId)
                            .flatMap { leggTilUføregrunnlagRequest ->
                                søknadsbehandlingService.leggTilUføregrunnlag(
                                    leggTilUføregrunnlagRequest,
                                ).mapLeft {
                                    when (it) {
                                        SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling -> HttpStatusCode.NotFound.errorJson(
                                            "fant ikke behandling",
                                            "fant_ikke_behandling",
                                        )
                                        is SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.UgyldigTilstand -> ugyldigTilstand(
                                            it.fra,
                                            it.til,
                                        )
                                        SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.UføregradOgForventetInntektMangler -> HttpStatusCode.BadRequest.errorJson(
                                            "Hvis man innvilger uførevilkåret må man sende med uføregrad og forventet inntekt",
                                            "uføregrad_og_forventet_inntekt_mangler",
                                        )
                                        SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.PeriodeForGrunnlagOgVurderingErForskjellig -> HttpStatusCode.BadRequest.errorJson(
                                            "Det er ikke samsvar mellom perioden for vurdering og perioden for grunnlaget",
                                            "periode_for_grunnlag_og_vurdering_er_forskjellig",
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
