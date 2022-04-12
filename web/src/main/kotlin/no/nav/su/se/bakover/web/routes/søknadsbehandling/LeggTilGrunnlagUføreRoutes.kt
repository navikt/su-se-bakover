package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.flatMap
import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
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
                                    it.mapFeil()
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

private fun SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.mapFeil(): Resultat {
    return when (this) {
        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling -> {
            Feilresponser.fantIkkeBehandling
        }
        is SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.UgyldigInput -> {
            when (this.originalFeil) {
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat -> {
                    Feilresponser.alleVurderingsperioderMåHaSammeResultat
                }
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.HeleBehandlingsperiodenMåHaVurderinger -> {
                    Feilresponser.heleBehandlingsperiodeMåHaVurderinger
                }
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder -> {
                    Feilresponser.overlappendeVurderingsperioder
                }
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> {
                    periodeForGrunnlagOgVurderingErForskjellig
                }
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> {
                    Feilresponser.Uføre.uføregradOgForventetInntektMangler
                }
                LeggTilUførevurderingerRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> {
                    Feilresponser.utenforBehandlingsperioden
                }
            }
        }
        is SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(fra = fra, til = til)
        }
        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> {
            Feilresponser.utenforBehandlingsperioden
        }
    }
}
