package no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningVurderinger
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SØKNADSBEHANDLING_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import vilkår.familiegjenforening.domain.UgyldigFamiliegjenforeningVilkår
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.leggTilFamiliegjenforeningRoute(
    søknadsbehandlingService: SøknadsbehandlingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/familiegjenforening") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<FamiliegjenforeningBody> { body ->
                    søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                        request = body.toLeggTilFamiliegjenforeningRequest(SøknadsbehandlingId(behandlingId)),
                        saksbehandler = call.suUserContext.saksbehandler,
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson(formuegrenserFactory))))
                        },
                    )
                }
            }
        }
    }
}

// TODO trekker ut til felles fil..
data class FamiliegjenforeningBody(
    val vurderinger: List<FamiliegjenforeningVurderingBody>,
) {
    fun toLeggTilFamiliegjenforeningRequest(behandlingId: BehandlingsId) =
        LeggTilFamiliegjenforeningRequest(
            behandlingId = behandlingId,
            vurderinger = vurderinger.map {
                FamiliegjenforeningVurderinger(
                    periode = it.periode.toPeriode(),
                    status = it.status,
                )
            },
        )
}

data class FamiliegjenforeningVurderingBody(
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
    is SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService.Domenefeil -> underliggende.tilResultat()
}

private fun UgyldigFamiliegjenforeningVilkår.tilResultat(): Resultat {
    return when (this) {
        UgyldigFamiliegjenforeningVilkår.OverlappendeVurderingsperioder -> Feilresponser.overlappendeVurderingsperioder
    }
}

fun KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.Vilkårsfeil -> this.underliggende.tilResultat()
    }
}
