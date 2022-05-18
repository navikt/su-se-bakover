package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.flatMap
import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.grunnlag.LeggTilUførervurderingerBody
import no.nav.su.se.bakover.web.routes.revurdering.tilResultat
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody

internal fun Route.leggTilUføregrunnlagRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/grunnlag/uføre") {
        authorize(Brukerrolle.Saksbehandler) {
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
                                    Resultat.json(
                                        HttpStatusCode.Created,
                                        serialize(it.toJson(satsFactory))
                                    )
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
        is SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.UgyldigInput -> this.originalFeil.tilResultat()
        is SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(fra = fra, til = til)
        }
        SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> {
            Feilresponser.utenforBehandlingsperioden
        }
    }
}
