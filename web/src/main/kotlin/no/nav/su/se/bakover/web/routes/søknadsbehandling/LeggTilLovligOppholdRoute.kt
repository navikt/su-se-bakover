package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageLovligOppholdVilkår
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.service.vilkår.LovligOppholdVilkårStatus
import no.nav.su.se.bakover.service.vilkår.LovligOppholdVurderinger
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import java.util.UUID

internal fun Route.leggTilLovligOppholdRoute(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/lovligOpphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<LovligOppholdBody> { body ->
                    søknadsbehandlingService.leggTilLovligOpphold(body.toLovligOppholdRequest(behandlingId)).fold(
                        ifLeft = {
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory))))
                        },
                    )
                }
            }
        }
    }
}

internal data class LovligOppholdBody(
    val vurderinger: List<LovligOppholdVurderingBody>,
) {
    fun toLovligOppholdRequest(behandlingId: UUID) = LeggTilLovligOppholdRequest(
        behandlingId = behandlingId, vurderinger = vurderinger.map { LovligOppholdVurderinger(it.status) },
    )
}

internal data class LovligOppholdVurderingBody(
    val status: LovligOppholdVilkårStatus,
)

internal fun SøknadsbehandlingService.KunneIkkeLeggetilLovligOppholdVilkår.tilResultat() = when (this) {
    SøknadsbehandlingService.KunneIkkeLeggetilLovligOppholdVilkår.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
    is SøknadsbehandlingService.KunneIkkeLeggetilLovligOppholdVilkår.FeilVedSøknadsbehandling -> this.feil.tilResultat()
    is SøknadsbehandlingService.KunneIkkeLeggetilLovligOppholdVilkår.UgyldigLovligOppholdVilkår -> this.feil.tilResultat()
}

internal fun KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.tilResultat() = when (this) {
    is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
        fra = this.fra,
        til = this.til,
    )
}

internal fun KunneIkkeLageLovligOppholdVilkår.tilResultat() = when (this) {
    KunneIkkeLageLovligOppholdVilkår.OverlappendeVurderingsperioder -> Feilresponser.overlappendeVurderingsperioder
    KunneIkkeLageLovligOppholdVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
}
