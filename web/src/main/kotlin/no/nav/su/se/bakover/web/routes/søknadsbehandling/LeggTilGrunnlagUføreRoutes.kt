package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.flatMap
import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.grunnlag.LeggTilUførervurderingerBody
import no.nav.su.se.bakover.web.routes.revurdering.tilResultat

internal fun Route.leggTilUføregrunnlagRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/uføregrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<LeggTilUførervurderingerBody> { body ->
                    call.svar(
                        body.toServiceCommand(behandlingId)
                            .flatMap { leggTilUføregrunnlagRequest ->
                                søknadsbehandlingService.leggTilUførevilkår(
                                    leggTilUføregrunnlagRequest,
                                    saksbehandler = call.suUserContext.saksbehandler,
                                ).mapLeft {
                                    it.mapFeil()
                                }.map {
                                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                    Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory)))
                                }
                            }.getOrHandle { it },
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
