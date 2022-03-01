package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.flatMap
import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.Uføre.periodeForGrunnlagOgVurderingErForskjellig
import no.nav.su.se.bakover.web.routes.grunnlag.LeggTilUførervurderingerBody
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody

internal fun Route.leggTilUføregrunnlagRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/grunnlag/uføre") {
            call.withBehandlingId { behandlingId ->
                call.withBody<LeggTilUførervurderingerBody> { body ->
                    call.svar(
                        body.toServiceCommand(behandlingId)
                            .flatMap { leggTilUføregrunnlagRequest ->
                                søknadsbehandlingService.leggTilUførevilkår(
                                    leggTilUføregrunnlagRequest,
                                ).mapLeft {
                                    when (it) {
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling -> {
                                            Feilresponser.fantIkkeBehandling
                                        }
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.UføregradOgForventetInntektMangler -> {
                                            Feilresponser.Uføre.uføregradOgForventetInntektMangler
                                        }
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.PeriodeForGrunnlagOgVurderingErForskjellig -> {
                                            periodeForGrunnlagOgVurderingErForskjellig
                                        }
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.OverlappendeVurderingsperioder -> {
                                            Feilresponser.overlappendeVurderingsperioder
                                        }
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> {
                                            Feilresponser.utenforBehandlingsperioden
                                        }
                                        is SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.UgyldigTilstand -> {
                                            Feilresponser.ugyldigTilstand(
                                                fra = it.fra,
                                                til = it.til,
                                            )
                                        }
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.AlleVurderingeneMåHaSammeResultat -> {
                                            Feilresponser.alleVurderingsperioderMåHaSammeResultat
                                        }
                                        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.HeleBehandlingsperiodenMåHaVurderinger -> {
                                            Feilresponser.heleBehandlingsperiodeMåHaVurderinger
                                        }
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
