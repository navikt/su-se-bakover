package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.web.routes.vilkår.FamiliegjenforeningVilkårRequest
import vilkår.familiegjenforening.domain.UgyldigFamiliegjenforeningVilkår
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.familiegjenforeningVilkårRoute(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    post("$REVURDERING_PATH/{revurderingId}/familiegjenforening") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<FamiliegjenforeningVilkårRequest> { body ->
                    call.svar(
                        revurderingService.leggTilFamiliegjenforeningvilkår(
                            request = body.toLeggTilFamiliegjenforeningRequest(RevurderingId(revurderingId)),
                        ).fold(
                            { it.tilResultat() },
                            {
                                call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id.value)
                                Resultat.json(HttpStatusCode.Created, it.json(formuegrenserFactory))
                            },
                        ),
                    )
                }
            }
        }
    }
}

private fun Revurdering.KunneIkkeLeggeTilFamiliegjenforeningVilkår.tilResultat() = when (this) {
    Revurdering.KunneIkkeLeggeTilFamiliegjenforeningVilkår.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
    is Revurdering.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
        this.fra,
        this.til,
    )

    is Revurdering.KunneIkkeLeggeTilFamiliegjenforeningVilkår.HeleBehandlingsperiodenErIkkeVurdert -> Feilresponser.vilkårMåVurderesForHeleBehandlingsperioden
    Revurdering.KunneIkkeLeggeTilFamiliegjenforeningVilkår.VilkårKunRelevantForAlder -> Feilresponser.vilkårKunRelevantForAlder
    is Revurdering.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigVilkår -> feil.tilResultat()
}

private fun UgyldigFamiliegjenforeningVilkår.tilResultat(): Resultat {
    return when (this) {
        UgyldigFamiliegjenforeningVilkår.OverlappendeVurderingsperioder -> Feilresponser.overlappendeVurderingsperioder
    }
}
