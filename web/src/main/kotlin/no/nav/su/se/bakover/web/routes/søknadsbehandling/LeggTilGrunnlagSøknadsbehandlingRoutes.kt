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
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import java.util.UUID

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
        post("$behandlingPath/{behandlingId}/grunnlag/uføre") {
            call.withBehandlingId { behandlingId ->
                call.withBody<UføregrunnlagBody> { body ->
                    call.svar(
                        body.toLeggTilUføregrunnlagRequest(behandlingId)
                            .flatMap { leggTilUføregrunnlagRequest ->
                                søknadsbehandlingService.leggTilUføregrunnlag(
                                    leggTilUføregrunnlagRequest,
                                ).mapLeft {
                                    when (it) {
                                        SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
                                        is SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.UgyldigTilstand -> ugyldigTilstand(
                                            it.fra,
                                            it.til,
                                        )
                                        SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.UføregradOgForventetInntektMangler -> Feilresponser.uføregradOgForventetInntektMangler
                                        SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.PeriodeForGrunnlagOgVurderingErForskjellig -> Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
                                        SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.OverlappendeVurderingsperioder -> Feilresponser.overlappendeVurderingsperioder
                                        SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> Feilresponser.utenforBehandlingsperioden
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
