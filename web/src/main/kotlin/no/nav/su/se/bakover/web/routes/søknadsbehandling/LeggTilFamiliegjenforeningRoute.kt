package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vilkår.UgyldigFamiliegjenforeningVilkår
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.FamiliegjenforeningVurderinger
import no.nav.su.se.bakover.service.vilkår.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.service.vilkår.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody

internal fun Route.leggTilFamiliegjenforeningRoute(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/familiegjenforening") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<FamiliegjenforeningBody> { body ->
                    søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                        request = LeggTilFamiliegjenforeningRequest(
                            behandlingId = behandlingId,
                            vurderinger = body.vurderinger.map {
                                FamiliegjenforeningVurderinger(
                                    it.periode.toPeriode(),
                                    it.status,
                                )
                            },
                        ),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory))))
                        },
                    )
                }
            }
        }
    }
}

private data class FamiliegjenforeningBody(
    val vurderinger: List<FamiliegjenforeningVurderingBody>,
)

private data class FamiliegjenforeningVurderingBody(
    val periode: PeriodeJson,
    val status: FamiliegjenforeningvilkårStatus,
)

private fun SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService.tilResultat() = when (this) {
    SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
    is SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
        this.fra,
        this.til,
    )
    is SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService.UgyldigFamiliegjenforeningVilkårService -> feil.tilResultat()
}

private fun UgyldigFamiliegjenforeningVilkår.tilResultat() = when (this) {
    UgyldigFamiliegjenforeningVilkår.OverlappendeVurderingsperioder -> Feilresponser.overlappendeVurderingsperioder
}
