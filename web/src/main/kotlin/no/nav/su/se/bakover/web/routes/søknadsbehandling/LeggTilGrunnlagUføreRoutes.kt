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
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.Uføre.periodeForGrunnlagOgVurderingErForskjellig
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import java.util.UUID

internal fun Route.leggTilUføregrunnlagRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    data class UføregrunnlagBody(
        val periode: PeriodeJson,
        val uføregrad: Int?,
        val forventetInntekt: Int?,
        val resultat: UførevilkårStatus,
        val begrunnelse: String,
    ) {

        fun toLeggTilUføregrunnlagRequest(behandlingId: UUID): Either<Resultat, LeggTilUførevilkårRequest> {
            return LeggTilUførevilkårRequest(
                behandlingId = behandlingId,
                periode = periode.toPeriode().getOrHandle {
                    return it.left()
                },
                uføregrad = uføregrad?.let {
                    Uføregrad.tryParse(it).getOrHandle {
                        return Feilresponser.Uføre.uføregradMåVæreMellomEnOgHundre.left()
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
                                søknadsbehandlingService.leggTilUførevilkår(
                                    leggTilUføregrunnlagRequest
                                ).mapLeft {
                                    when (it) {
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.UføregradOgForventetInntektMangler -> Feilresponser.Uføre.uføregradOgForventetInntektMangler
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.PeriodeForGrunnlagOgVurderingErForskjellig -> periodeForGrunnlagOgVurderingErForskjellig
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.OverlappendeVurderingsperioder -> Feilresponser.overlappendeVurderingsperioder
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> Feilresponser.utenforBehandlingsperioden
                                        is SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.UgyldigTilstand -> Feilresponser.ugyldigTilstand(it.fra, it.til)
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
