package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilOppholdIUtlandetRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import java.util.UUID

private data class OppholdIUtlandetBody(
    val status: LeggTilOppholdIUtlandetRequest.Status,
    val begrunnelse: String?,
) {
    fun toRequest(behandlingId: UUID): LeggTilOppholdIUtlandetRequest {
        return LeggTilOppholdIUtlandetRequest(
            behandlingId = behandlingId,
            status = status,
            begrunnelse = begrunnelse,
        )
    }
}

internal fun Route.leggTilOppholdIUtlandet(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/vilkår/oppholdIUtlandet") {
            call.withBehandlingId { behandlingId ->
                call.withBody<OppholdIUtlandetBody> { body ->
                    søknadsbehandlingService.leggTilOppholdIUtlandet(body.toRequest(behandlingId))
                        .mapLeft {
                            call.svar(
                                when (it) {
                                    SøknadsbehandlingService.KunneIkkeLeggeTilOppholdIUtlandet.FantIkkeBehandling -> {
                                        Feilresponser.fantIkkeBehandling
                                    }
                                    SøknadsbehandlingService.KunneIkkeLeggeTilOppholdIUtlandet.OverlappendeVurderingsperioder -> {
                                        Feilresponser.overlappendeVurderingsperioder
                                    }
                                    SøknadsbehandlingService.KunneIkkeLeggeTilOppholdIUtlandet.PeriodeForGrunnlagOgVurderingErForskjellig -> {
                                        Feilresponser.utenforBehandlingsperioden
                                    }
                                    is SøknadsbehandlingService.KunneIkkeLeggeTilOppholdIUtlandet.UgyldigTilstand -> {
                                        Feilresponser.ugyldigTilstand(fra = it.fra, til = it.til)
                                    }
                                },
                            )
                        }.map {
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                        }
                }
            }
        }
    }
}
